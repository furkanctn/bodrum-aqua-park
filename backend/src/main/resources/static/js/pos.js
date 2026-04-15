(function () {
	const TOKEN_KEY = "aqua_token";
	const USER_KEY = "aqua_user";
	const ROLE_KEY = "aqua_role";

	function authHeaders() {
		var t = sessionStorage.getItem(TOKEN_KEY);
		return {
			Authorization: "Bearer " + t,
			Accept: "application/json",
		};
	}

	function authHeadersJson() {
		return Object.assign({}, authHeaders(), { "Content-Type": "application/json" });
	}

	if (!sessionStorage.getItem(TOKEN_KEY)) {
		window.location.replace("/index.html");
		return;
	}

	const TICKETS = [
		{ id: "t1", label: "0–6 Yaş", price: 0 },
		{ id: "t2", label: "7–12 Yaş", price: 1250 },
		{ id: "t3", label: "Yetişkin", price: 1500 },
		{ id: "t4", label: "İndirimli yetişkin", price: 1400 },
		{ id: "t5", label: "Broşür yetişkin", price: 1350 },
		{ id: "t6", label: "%50 yetişkin", price: 750 },
		{ id: "t7", label: "%15 · 7–12", price: 1050 },
		{ id: "t8", label: "%20 yetişkin", price: 1200 },
		{ id: "t9", label: "%20 · 7–12", price: 1000 },
		{ id: "t10", label: "Öğrenci", price: 1100 },
		{ id: "t11", label: "65+ Yaş", price: 950 },
		{ id: "t12", label: "Aile paketi (3 kişi)", price: 3800 },
	];

	const fmt = new Intl.NumberFormat("tr-TR", {
		style: "currency",
		currency: "TRY",
		minimumFractionDigits: 2,
	});

	const MAX_KURUS = 999999999999;

	function money(n) {
		return fmt.format(n);
	}

	function escapeHtml(s) {
		if (s == null) {
			return "";
		}
		var d = document.createElement("div");
		d.textContent = String(s);
		return d.innerHTML;
	}

	let currentModule = "kart";
	let cart = [];
	let selectedTileId = null;
	let payMode = "cash";
	let discountPercent = 0;

	/** Bakiye: tutar (kuruş, tamsayı) */
	let keypadValue = 0;
	let bakiyePayMode = "cash";

	const gridEl = document.getElementById("pos-grid");
	const userEl = document.getElementById("footer-user-name");
	const clockEl = document.getElementById("footer-clock");
	const toastEl = document.getElementById("pos-toast");
	const viewKart = document.getElementById("view-kart");
	const viewBakiye = document.getElementById("view-bakiye");
	const viewSorgu = document.getElementById("view-sorgu");
	const viewUrun = document.getElementById("view-urun");
	const footerPrimaryLabel = document.getElementById("footer-primary-label");
	const bakiyeDisplay = document.getElementById("bakiye-display");
	const bakiyeKeypadEl = document.getElementById("bakiye-keypad");

	function readBool(key, def) {
		var v = sessionStorage.getItem(key);
		if (v === null || v === "") return def;
		return v === "true" || v === "1";
	}
	var ticketSales = readBool("aqua_ticket_sales", true);
	var balanceLoad = readBool("aqua_balance_load", true);
	var saleAreas = [];
	try {
		saleAreas = JSON.parse(sessionStorage.getItem("aqua_sale_areas") || "[]");
	} catch (e) {
		saleAreas = [];
	}
	/** API’den gelen satış alanı adları (kod → ad) */
	var saleAreaNamesByCode = {};
	/** "tickets" | "products" — kart görünümünde bilet mi ürün mü */
	var kartMode = "tickets";
	var kartProductAreaCode = null;
	var kartProducts = [];
	/** Son yüklenen ürün ızgarası alanı (gereksiz tekrar isteği önlemek için) */
	var kartCacheArea = null;

	function initKartMode() {
		kartMode = ticketSales ? "tickets" : saleAreas.length > 0 ? "products" : "tickets";
		if (kartMode === "products") {
			kartProductAreaCode = saleAreas[0] || null;
			discountPercent = 0;
		}
		syncKartViewUi();
	}

	function updateKartNavLabel() {
		var el = document.getElementById("nav-kart-label");
		var navKart = document.getElementById("nav-kart");
		if (!el) return;
		var label = "Kart satış";
		if (ticketSales) {
			label = "Kart satış";
		} else if (saleAreas.length === 1) {
			label = saleAreaNamesByCode[saleAreas[0]] || saleAreas[0];
		} else if (saleAreas.length > 1) {
			label = "Satış";
		}
		el.textContent = label;
		if (navKart) navKart.setAttribute("title", label);
	}

	function syncKartViewUi() {
		var tabs = document.getElementById("kart-area-tabs");
		var gh = document.getElementById("grid-heading");
		if (kartMode === "products") {
			if (tabs) {
				tabs.hidden = saleAreas.length <= 1;
				renderKartAreaTabs();
			}
			if (gh && kartProductAreaCode) {
				var nm = saleAreaNamesByCode[kartProductAreaCode] || kartProductAreaCode;
				gh.textContent = nm + " — ürünler";
			} else if (gh) {
				gh.textContent = "Ürünler";
			}
		} else {
			if (tabs) tabs.hidden = true;
			if (gh) gh.textContent = "Bilet ve yaş grupları";
		}
	}

	function renderKartAreaTabs() {
		var tabs = document.getElementById("kart-area-tabs");
		if (!tabs || saleAreas.length <= 1) return;
		tabs.innerHTML = "";
		saleAreas.forEach(function (code) {
			var b = document.createElement("button");
			b.type = "button";
			b.className = "kart-area-tab" + (code === kartProductAreaCode ? " active" : "");
			b.setAttribute("role", "tab");
			b.setAttribute("data-code", code);
			b.setAttribute("aria-selected", code === kartProductAreaCode ? "true" : "false");
			b.textContent = saleAreaNamesByCode[code] || code;
			b.addEventListener("click", function () {
				if (code === kartProductAreaCode) return;
				kartProductAreaCode = code;
				cart = [];
				selectedTileId = null;
				loadKartProductsForArea(code, function () {
					renderGrid();
					updateSummary();
					syncKartViewUi();
					updateKartNavLabel();
					updateContextBar();
				});
			});
			tabs.appendChild(b);
		});
	}

	function loadKartProductsForArea(code, done) {
		if (!code) {
			kartProducts = [];
			if (done) done();
			return;
		}
		fetch("/api/products?saleAreaCode=" + encodeURIComponent(code), { headers: authHeaders() })
			.then(function (r) {
				if (r.status === 401) {
					window.location.replace("/index.html");
					return null;
				}
				if (!r.ok) {
					throw new Error("http");
				}
				return r.json();
			})
			.then(function (items) {
				kartProducts = items || [];
				kartCacheArea = code;
				if (done) done();
			})
			.catch(function () {
				kartProducts = [];
				kartCacheArea = null;
				showToast("Ürünler yüklenemedi");
				if (done) done();
			});
	}

	function effectiveDiscount() {
		return currentModule === "kart" && kartMode === "tickets" ? discountPercent : 0;
	}

	function getKartBadgeLabel() {
		if (kartMode === "products" && kartProductAreaCode) {
			return saleAreaNamesByCode[kartProductAreaCode] || kartProductAreaCode;
		}
		return "Kart satış";
	}

	function getKartBadgeHint() {
		if (kartMode === "products") {
			return "Ürün seçin; Satışı tamamlayın — açılan pencerede kartı okutun. Bakiye yeterliyse satış işlenir.";
		}
		return "Tarifeye dokunun; Satışı tamamla ile açılan pencerede kartı okutun veya UID girin.";
	}

	function sumProductCartTotal() {
		var sum = 0;
		cart.forEach(function (c) {
			if (c.productId != null) {
				var p = Number(c.price);
				if (!isNaN(p)) {
					sum += p;
				}
			}
		});
		return sum;
	}

	function completeProductCart(uid) {
		var items = cart.filter(function (c) {
			return c.productId != null;
		});
		if (!items.length) return;
		var i = 0;
		var totalPaid = 0;
		var lineStrs = [];
		var lastBalanceAfter = null;
		function next() {
			if (i >= items.length) {
				cart = [];
				selectedTileId = null;
				discountPercent = 0;
				renderGrid();
				updateSummary();
				var receipt = buildProductSaleInfoReceipt(lineStrs, totalPaid, lastBalanceAfter);
				showToast(receipt, { multiline: true, duration: 7000 });
				sendSaleReceiptToPrinter(receipt);
				return;
			}
			fetch("/api/sales", {
				method: "POST",
				headers: authHeadersJson(),
				body: JSON.stringify({ cardUid: uid, productId: items[i].productId }),
			})
				.then(function (r) {
					return r.json().then(function (data) {
						return { ok: r.ok, status: r.status, data: data };
					});
				})
				.then(function (res) {
					if (res.status === 401) {
						window.location.replace("/index.html");
						return;
					}
					if (res.status === 403) {
						showToast((res.data && (res.data.detail || res.data.message)) || "Bu ürün için yetkiniz yok");
						return;
					}
					if (res.status === 409) {
						var d409 = res.data || {};
						var insMsg = "Yetersiz bakiye";
						if (d409.balance != null && d409.required != null) {
							insMsg =
								"Yetersiz bakiye. Bakiye: " +
								money(Number(d409.balance)) +
								" · Gerekli: " +
								money(Number(d409.required));
						} else if (d409.detail && typeof d409.detail === "string") {
							insMsg = d409.detail;
						}
						showToast(insMsg);
						return;
					}
					if (!res.ok) {
						var msg =
							(res.data && (res.data.detail || res.data.message || res.data.error)) || "Satış yapılamadı";
						showToast(typeof msg === "string" ? msg : "Satış yapılamadı");
						return;
					}
					var d = res.data || {};
					var amt = d.amount != null ? Number(d.amount) : NaN;
					if (!isNaN(amt)) {
						totalPaid += amt;
					}
					var label = d.productName ? String(d.productName) : "Ürün";
					lineStrs.push(label + ": " + money(isNaN(amt) ? 0 : amt));
					if (d.balanceAfter != null) {
						lastBalanceAfter = Number(d.balanceAfter);
					}
					i++;
					next();
				})
				.catch(function () {
					showToast("İstek başarısız");
				});
		}
		next();
	}

	function applyNavPermissions() {
		var navKart = document.getElementById("nav-kart");
		if (navKart) navKart.hidden = !(ticketSales || saleAreas.length > 0);
		var navBakiyeSlot = document.getElementById("nav-bakiye-slot");
		var navBakiyeLabel = document.getElementById("nav-bakiye-label");
		if (navBakiyeSlot) {
			if (balanceLoad) {
				navBakiyeSlot.hidden = false;
				navBakiyeSlot.setAttribute("data-module", "bakiye");
				navBakiyeSlot.removeAttribute("data-preset-area");
				navBakiyeSlot.title = "Bakiye yükleme";
				if (navBakiyeLabel) navBakiyeLabel.textContent = "Bakiye yükleme";
			} else {
				navBakiyeSlot.hidden = true;
			}
		}
		var btnGoBak = document.getElementById("btn-urun-go-bakiye");
		if (btnGoBak) btnGoBak.hidden = !balanceLoad;
		updateKartNavLabel();
	}
	function pickInitialModule() {
		if (ticketSales) return "kart";
		if (saleAreas.length > 0) return "kart";
		if (balanceLoad) return "bakiye";
		return "urun";
	}
	initKartMode();
	applyNavPermissions();

	const elDiscount = document.getElementById("sum-discount");
	const elDue = document.getElementById("sum-due");
	const elCash = document.getElementById("sum-cash");
	const elCard = document.getElementById("sum-card");
	const elCredit = document.getElementById("sum-credit");
	const elChange = document.getElementById("sum-change");

	const bakSumTotal = document.getElementById("bak-sum-total");
	const bakSumCash = document.getElementById("bak-sum-cash");
	const bakSumCard = document.getElementById("bak-sum-card");
	const bakSumChange = document.getElementById("bak-sum-change");

	const sorguDisplay = document.getElementById("sorgu-display");
	const sorguKeypadEl = document.getElementById("sorgu-keypad");

	/** Sadece rakam, max 16 (kart UID) */
	let sorguDigits = "";
	const MAX_SORGU_DIGITS = 16;

	let urunCardUid = "";
	/** Aynı alana ikinci HID okuma (üst üste rakam) — yüklemeden önce engel; tam uzunluk için Temizle */
	const URUN_UID_SCAN_BLOCK_LEN = 10;

	userEl.textContent = sessionStorage.getItem("aqua_display_name") || sessionStorage.getItem(USER_KEY) || "—";

	var navAdmin = document.getElementById("nav-admin");
	if (navAdmin) {
		navAdmin.hidden = sessionStorage.getItem(ROLE_KEY) !== "ADMIN";
	}

	function showToast(msg, opts) {
		opts = opts || {};
		toastEl.textContent = msg;
		toastEl.classList.toggle("toast--block", !!opts.multiline);
		toastEl.classList.add("visible");
		clearTimeout(showToast._t);
		var ms =
			typeof opts.duration === "number"
				? opts.duration
				: opts.multiline
					? 6500
					: 2600;
		showToast._t = setTimeout(function () {
			toastEl.classList.remove("visible");
			toastEl.classList.remove("toast--block");
		}, ms);
	}

	/** aria-hidden ile gizlenmeden önce; odak içeride kalırsa konsol uyarısı oluşmasın */
	function blurFocusInsideOverlay(overlayEl) {
		if (!overlayEl || typeof overlayEl.contains !== "function") {
			return;
		}
		try {
			var ae = document.activeElement;
			if (ae && overlayEl.contains(ae) && typeof ae.blur === "function") {
				ae.blur();
			}
		} catch (e) {}
	}

	function buildProductSaleInfoReceipt(lineStrs, totalPaid, balanceAfter) {
		var bal =
			balanceAfter != null && !isNaN(balanceAfter)
				? money(balanceAfter)
				: "—";
		var paid = totalPaid != null && !isNaN(totalPaid) ? money(totalPaid) : "—";
		return (
			"Bilgi fişi\n" +
			"────────\n" +
			lineStrs.join("\n") +
			"\n────────\n" +
			"Ödenen: " +
			paid +
			"\n" +
			"Kalan bakiye: " +
			bal
		);
	}

	/** Bilgi fişini ESC/POS yazıcıya gönderir (port: sessionStorage veya sunucu app.printer.port). */
	function sendSaleReceiptToPrinter(receiptText) {
		var lines = receiptText.split("\n");
		if (lines.length > 48) {
			lines = lines.slice(0, 48);
		}
		var body = { lines: lines, mode: "nocut" };
		var pp = sessionStorage.getItem("aqua_printer_port");
		var bb = sessionStorage.getItem("aqua_printer_baud");
		if (pp) {
			body.port = pp;
		}
		if (bb) {
			body.baudRate = parseInt(bb, 10);
		}
		fetch("/api/printer/sale-receipt", {
			method: "POST",
			headers: authHeadersJson(),
			body: JSON.stringify(body),
		})
			.then(function (r) {
				return r.json().then(function (data) {
					return { httpOk: r.ok, status: r.status, data: data };
				});
			})
			.then(function (res) {
				var d = res.data || {};
				if (!res.httpOk || d.ok === false) {
					var err = d.error || d.detail || "Fiş yazdırılamadı";
					showToast(err, { duration: 4000 });
					return;
				}
				var okMsg = d.message || "Fiş yazıcıya gönderildi";
				if (d.port) {
					okMsg += " · " + d.port + (d.baudRate ? " @ " + d.baudRate : "");
				}
				showToast(okMsg, { duration: 3500 });
			})
			.catch(function () {
				showToast("Fiş yazıcıya ulaşılamadı (ağ / sunucu)", { duration: 3500 });
			});
	}

	var rfidReadAbort = null;
	var rfidReadInProgress = false;
	var rfidOverlayEl = document.getElementById("rfid-read-overlay");
	var rfidMsgEl = document.getElementById("rfid-read-msg");
	var rfidInputEl = document.getElementById("rfid-read-input");

	/** Bilet + kart satış yetkisi: sipariş tamamla → kart tanımlama modalı (HID klavye kama) */
	var ticketCardBindOverlay = document.getElementById("ticket-card-bind-overlay");
	var ticketCardBindInput = document.getElementById("ticket-card-bind-input");
	var ticketCardBindConfirmBtn = document.getElementById("ticket-card-bind-confirm");
	var ticketCardBindCancelBtn = document.getElementById("ticket-card-bind-cancel");
	var ticketCardBindSubmitting = false;
	var ticketCardBindIdleTimer = null;
	var TICKET_BIND_IDLE_MS = 150;
	var TICKET_BIND_IDLE_MIN_LEN = 4;

	function clearTicketCardBindIdle() {
		if (ticketCardBindIdleTimer) {
			clearTimeout(ticketCardBindIdleTimer);
			ticketCardBindIdleTimer = null;
		}
	}

	function scheduleTicketCardBindIdle() {
		if (!ticketCardBindOverlay || ticketCardBindOverlay.hidden || !ticketCardBindInput) {
			return;
		}
		clearTicketCardBindIdle();
		ticketCardBindIdleTimer = setTimeout(function () {
			ticketCardBindIdleTimer = null;
			if (!ticketCardBindOverlay || ticketCardBindOverlay.hidden || ticketCardBindSubmitting) {
				return;
			}
			var v = cleanUid(ticketCardBindInput.value);
			if (v.length >= TICKET_BIND_IDLE_MIN_LEN) {
				confirmTicketCardBind();
			}
		}, TICKET_BIND_IDLE_MS);
	}

	function openTicketCardBindModal() {
		if (!ticketCardBindOverlay || !ticketCardBindInput) {
			return;
		}
		clearTicketCardBindIdle();
		ticketCardBindSubmitting = false;
		if (ticketCardBindConfirmBtn) {
			ticketCardBindConfirmBtn.disabled = false;
		}
		if (ticketCardBindCancelBtn) {
			ticketCardBindCancelBtn.disabled = false;
		}
		ticketCardBindInput.value = "";
		ticketCardBindOverlay.hidden = false;
		ticketCardBindOverlay.setAttribute("aria-hidden", "false");
		setTimeout(function () {
			ticketCardBindInput.focus();
		}, 30);
		setTimeout(function () {
			ticketCardBindInput.focus();
		}, 120);
	}

	function closeTicketCardBindModal() {
		clearTicketCardBindIdle();
		ticketCardBindSubmitting = false;
		if (ticketCardBindOverlay) {
			blurFocusInsideOverlay(ticketCardBindOverlay);
			ticketCardBindOverlay.hidden = true;
			ticketCardBindOverlay.setAttribute("aria-hidden", "true");
		}
		if (ticketCardBindInput) {
			ticketCardBindInput.value = "";
			try {
				ticketCardBindInput.blur();
			} catch (e) {}
		}
	}

	/**
	 * Bilet satışı: karta turnike giriş hakkı (entryGate) yazar.
	 * @returns {Promise<boolean>} başarılıysa true
	 */
	function completeTicketSale(uid) {
		var uidT = cleanUid(uid);
		if (!uidT.length) {
			showToast("Kart UID gerekli");
			return Promise.resolve(false);
		}
		var sub = subtotal();
		var disc = sub * effectiveDiscount();
		return fetch("/api/cards/" + encodeURIComponent(uidT) + "/ticket-entry-grant", {
			method: "POST",
			headers: authHeadersJson(),
		})
			.then(function (r) {
				if (r.status === 401) {
					window.location.replace("/index.html");
					return null;
				}
				return r.json().then(function (data) {
					return { ok: r.ok, status: r.status, data: data };
				});
			})
			.then(function (res) {
				if (!res) {
					return false;
				}
				if (!res.ok) {
					var msg = (res.data && (res.data.detail || res.data.message || res.data.error)) || "Kayıt yapılamadı";
					showToast(typeof msg === "string" ? msg : "Kayıt yapılamadı");
					return false;
				}
				cart = [];
				selectedTileId = null;
				discountPercent = 0;
				renderGrid();
				updateSummary();
				var eg =
					res.data && typeof res.data.entryGate === "number"
						? res.data.entryGate
						: res.data && res.data.entryGate != null
							? Number(res.data.entryGate)
							: null;
				var line =
					"Bilet kaydedildi · " +
					money(sub - disc) +
					" · turnike giriş hakkı kartta" +
					(eg != null ? " (" + eg + ")" : "") +
					" · UID " +
					uidT;
				showToast(line);
				return true;
			})
			.catch(function (err) {
				if (err && err.name === "TypeError") {
					showToast("Sunucuya bağlanılamadı");
					return false;
				}
				showToast("İstek başarısız");
				return false;
			});
	}

	function confirmTicketCardBind() {
		if (!ticketCardBindInput || ticketCardBindSubmitting) {
			return;
		}
		var uid = ticketCardBindInput.value;
		if (!cleanUid(uid).length) {
			showToast("Kartı okutun veya UID girin");
			ticketCardBindInput.focus();
			return;
		}
		clearTicketCardBindIdle();
		ticketCardBindSubmitting = true;
		if (ticketCardBindConfirmBtn) {
			ticketCardBindConfirmBtn.disabled = true;
		}
		if (ticketCardBindCancelBtn) {
			ticketCardBindCancelBtn.disabled = true;
		}
		completeTicketSale(uid).then(function (ok) {
			ticketCardBindSubmitting = false;
			if (ticketCardBindConfirmBtn) {
				ticketCardBindConfirmBtn.disabled = false;
			}
			if (ticketCardBindCancelBtn) {
				ticketCardBindCancelBtn.disabled = false;
			}
			if (ok) {
				closeTicketCardBindModal();
			} else if (ticketCardBindInput) {
				ticketCardBindInput.focus();
			}
		});
	}

	if (ticketCardBindConfirmBtn) {
		ticketCardBindConfirmBtn.addEventListener("click", function () {
			clearTicketCardBindIdle();
			confirmTicketCardBind();
		});
	}
	if (ticketCardBindCancelBtn) {
		ticketCardBindCancelBtn.addEventListener("click", function () {
			closeTicketCardBindModal();
		});
	}
	if (ticketCardBindInput) {
		ticketCardBindInput.addEventListener("input", scheduleTicketCardBindIdle);
		ticketCardBindInput.addEventListener("keydown", function (e) {
			if (e.key === "Enter") {
				e.preventDefault();
				clearTicketCardBindIdle();
				confirmTicketCardBind();
			}
		});
	}
	/** Bakiye yükleme: Yüklemeyi tamamla → kart UID (HID) */
	var bakiyeCardBindOverlay = document.getElementById("bakiye-card-bind-overlay");
	var bakiyeCardBindInput = document.getElementById("bakiye-card-bind-input");
	var bakiyeCardBindConfirmBtn = document.getElementById("bakiye-card-bind-confirm");
	var bakiyeCardBindCancelBtn = document.getElementById("bakiye-card-bind-cancel");
	var bakiyeCardBindSubmitting = false;
	var bakiyeCardBindIdleTimer = null;
	var BAKIYE_BIND_IDLE_MS = 150;
	var BAKIYE_BIND_IDLE_MIN_LEN = 4;

	function clearBakiyeCardBindIdle() {
		if (bakiyeCardBindIdleTimer) {
			clearTimeout(bakiyeCardBindIdleTimer);
			bakiyeCardBindIdleTimer = null;
		}
	}

	function scheduleBakiyeCardBindIdle() {
		if (!bakiyeCardBindOverlay || bakiyeCardBindOverlay.hidden || !bakiyeCardBindInput) {
			return;
		}
		clearBakiyeCardBindIdle();
		bakiyeCardBindIdleTimer = setTimeout(function () {
			bakiyeCardBindIdleTimer = null;
			if (!bakiyeCardBindOverlay || bakiyeCardBindOverlay.hidden || bakiyeCardBindSubmitting) {
				return;
			}
			var v = cleanUid(bakiyeCardBindInput.value);
			if (v.length >= BAKIYE_BIND_IDLE_MIN_LEN) {
				confirmBakiyeCardBind();
			}
		}, BAKIYE_BIND_IDLE_MS);
	}

	function openBakiyeCardBindModal() {
		if (!balanceLoad) {
			showToast("Bakiye yükleme yetkiniz yok");
			return;
		}
		if (keypadValue <= 0) {
			showToast("Yüklenecek tutarı girin");
			return;
		}
		if (!bakiyeCardBindOverlay || !bakiyeCardBindInput) {
			return;
		}
		clearBakiyeCardBindIdle();
		bakiyeCardBindSubmitting = false;
		if (bakiyeCardBindConfirmBtn) {
			bakiyeCardBindConfirmBtn.disabled = false;
		}
		if (bakiyeCardBindCancelBtn) {
			bakiyeCardBindCancelBtn.disabled = false;
		}
		bakiyeCardBindInput.value = "";
		bakiyeCardBindOverlay.hidden = false;
		bakiyeCardBindOverlay.setAttribute("aria-hidden", "false");
		setTimeout(function () {
			bakiyeCardBindInput.focus();
		}, 30);
		setTimeout(function () {
			bakiyeCardBindInput.focus();
		}, 120);
	}

	function closeBakiyeCardBindModal() {
		clearBakiyeCardBindIdle();
		bakiyeCardBindSubmitting = false;
		if (bakiyeCardBindOverlay) {
			blurFocusInsideOverlay(bakiyeCardBindOverlay);
			bakiyeCardBindOverlay.hidden = true;
			bakiyeCardBindOverlay.setAttribute("aria-hidden", "true");
		}
		if (bakiyeCardBindInput) {
			bakiyeCardBindInput.value = "";
			try {
				bakiyeCardBindInput.blur();
			} catch (e) {}
		}
	}

	/**
	 * Bakiye yükleme API; başarıda keypad sıfırlanır.
	 * @returns {Promise<boolean>}
	 */
	function performBakiyeLoad(uid) {
		var uidT = cleanUid(uid);
		if (!uidT.length) {
			showToast("Kartı okutun veya UID girin");
			return Promise.resolve(false);
		}
		if (!balanceLoad) {
			showToast("Bakiye yükleme yetkiniz yok");
			return Promise.resolve(false);
		}
		if (keypadValue <= 0) {
			showToast("Yüklenecek tutarı girin");
			return Promise.resolve(false);
		}
		var amount = Math.round(keypadValue) / 100;
		return fetch("/api/cards/" + encodeURIComponent(uidT) + "/balance-load", {
			method: "POST",
			headers: authHeadersJson(),
			body: JSON.stringify({
				amount: amount,
				paymentMethod: bakiyePayMode,
			}),
		})
			.then(function (r) {
				if (r.status === 401) {
					window.location.replace("/index.html");
					return null;
				}
				return r.json()
					.catch(function () {
						return {};
					})
					.then(function (data) {
						return { ok: r.ok, status: r.status, data: data };
					});
			})
			.then(function (res) {
				if (!res) {
					return false;
				}
				if (!res.ok) {
					var d = res.data || {};
					var msg = d.detail || d.message || d.title || "Yükleme yapılamadı";
					showToast(typeof msg === "string" ? msg : "Yükleme yapılamadı");
					return false;
				}
				var bal = res.data && res.data.balance != null ? Number(res.data.balance) : null;
				bakiyeClear();
				var t = "Bakiye yüklendi · " + money(amount);
				if (bal != null && !isNaN(bal)) {
					t += " · Yeni bakiye: " + money(bal);
				}
				showToast(t);
				return true;
			})
			.catch(function (err) {
				if (err && err.name === "TypeError") {
					showToast("Sunucuya bağlanılamadı");
					return false;
				}
				showToast("İstek başarısız");
				return false;
			});
	}

	function confirmBakiyeCardBind() {
		if (!bakiyeCardBindInput || bakiyeCardBindSubmitting) {
			return;
		}
		var uid = bakiyeCardBindInput.value;
		if (!cleanUid(uid).length) {
			showToast("Kartı okutun veya UID girin");
			bakiyeCardBindInput.focus();
			return;
		}
		clearBakiyeCardBindIdle();
		bakiyeCardBindSubmitting = true;
		if (bakiyeCardBindConfirmBtn) {
			bakiyeCardBindConfirmBtn.disabled = true;
		}
		if (bakiyeCardBindCancelBtn) {
			bakiyeCardBindCancelBtn.disabled = true;
		}
		performBakiyeLoad(uid).then(function (ok) {
			bakiyeCardBindSubmitting = false;
			if (bakiyeCardBindConfirmBtn) {
				bakiyeCardBindConfirmBtn.disabled = false;
			}
			if (bakiyeCardBindCancelBtn) {
				bakiyeCardBindCancelBtn.disabled = false;
			}
			if (ok) {
				closeBakiyeCardBindModal();
			} else if (bakiyeCardBindInput) {
				bakiyeCardBindInput.focus();
			}
		});
	}

	if (bakiyeCardBindConfirmBtn) {
		bakiyeCardBindConfirmBtn.addEventListener("click", function () {
			clearBakiyeCardBindIdle();
			confirmBakiyeCardBind();
		});
	}
	if (bakiyeCardBindCancelBtn) {
		bakiyeCardBindCancelBtn.addEventListener("click", function () {
			closeBakiyeCardBindModal();
		});
	}
	if (bakiyeCardBindInput) {
		bakiyeCardBindInput.addEventListener("input", scheduleBakiyeCardBindIdle);
		bakiyeCardBindInput.addEventListener("keydown", function (e) {
			if (e.key === "Enter") {
				e.preventDefault();
				clearBakiyeCardBindIdle();
				confirmBakiyeCardBind();
			}
		});
	}

	/** Ürün satışı (FIRIN vb.): Satışı tamamla → kart okut → bakiye ≥ sepet */
	var productSaleCardOverlay = document.getElementById("product-sale-card-overlay");
	var productSaleCardSummary = document.getElementById("product-sale-card-summary");
	var productSaleCardBalanceLine = document.getElementById("product-sale-card-balance-line");
	var productSaleCardError = document.getElementById("product-sale-card-error");
	var productSaleCardInput = document.getElementById("product-sale-card-input");
	var productSaleCardConfirmBtn = document.getElementById("product-sale-card-confirm");
	var productSaleCardCancelBtn = document.getElementById("product-sale-card-cancel");
	var productSaleCardSubmitting = false;
	var productSalePendingTotal = 0;
	var productSaleCardIdleTimer = null;
	var productSaleCardAbort = null;
	var PRODUCT_SALE_CARD_IDLE_MS = 150;
	var PRODUCT_SALE_CARD_IDLE_MIN_LEN = 4;

	function abortProductSaleCardFetch() {
		if (productSaleCardAbort) {
			try {
				productSaleCardAbort.abort();
			} catch (e) {}
			productSaleCardAbort = null;
		}
	}

	function clearProductSaleCardIdle() {
		if (productSaleCardIdleTimer) {
			clearTimeout(productSaleCardIdleTimer);
			productSaleCardIdleTimer = null;
		}
	}

	function scheduleProductSaleCardIdle() {
		if (!productSaleCardOverlay || productSaleCardOverlay.hidden || !productSaleCardInput) {
			return;
		}
		clearProductSaleCardIdle();
		productSaleCardIdleTimer = setTimeout(function () {
			productSaleCardIdleTimer = null;
			if (!productSaleCardOverlay || productSaleCardOverlay.hidden || productSaleCardSubmitting) {
				return;
			}
			var v = cleanUid(productSaleCardInput.value);
			if (v.length >= PRODUCT_SALE_CARD_IDLE_MIN_LEN) {
				confirmProductSaleCard();
			}
		}, PRODUCT_SALE_CARD_IDLE_MS);
	}

	function resetProductSaleCardModalErrors() {
		if (productSaleCardError) {
			productSaleCardError.hidden = true;
			productSaleCardError.textContent = "";
		}
		if (productSaleCardBalanceLine) {
			productSaleCardBalanceLine.hidden = true;
			productSaleCardBalanceLine.textContent = "";
		}
	}

	function openProductSaleCardModal() {
		if (!productSaleCardOverlay || !productSaleCardInput) {
			return;
		}
		var total = sumProductCartTotal();
		if (total <= 0) {
			showToast("Sepet tutarı geçersiz");
			return;
		}
		productSalePendingTotal = total;
		clearProductSaleCardIdle();
		productSaleCardSubmitting = false;
		resetProductSaleCardModalErrors();
		if (productSaleCardSummary) {
			productSaleCardSummary.textContent =
				"Sepet tutarı: " +
				money(total) +
				". Kartı okutun veya UID yazıp Enter’a basın; bakiye yeterliyse işlem tamamlanır.";
		}
		productSaleCardInput.value = "";
		if (productSaleCardConfirmBtn) {
			productSaleCardConfirmBtn.disabled = false;
		}
		productSaleCardOverlay.hidden = false;
		productSaleCardOverlay.setAttribute("aria-hidden", "false");
		setTimeout(function () {
			productSaleCardInput.focus();
		}, 30);
		setTimeout(function () {
			productSaleCardInput.focus();
		}, 120);
	}

	function closeProductSaleCardModal() {
		abortProductSaleCardFetch();
		clearProductSaleCardIdle();
		productSaleCardSubmitting = false;
		productSalePendingTotal = 0;
		resetProductSaleCardModalErrors();
		if (productSaleCardOverlay) {
			blurFocusInsideOverlay(productSaleCardOverlay);
			productSaleCardOverlay.hidden = true;
			productSaleCardOverlay.setAttribute("aria-hidden", "true");
		}
		if (productSaleCardInput) {
			productSaleCardInput.value = "";
			try {
				productSaleCardInput.blur();
			} catch (e) {}
		}
	}

	function confirmProductSaleCard() {
		if (!productSaleCardInput || productSaleCardSubmitting) {
			return;
		}
		var uid = cleanUid(productSaleCardInput.value);
		if (!uid.length) {
			showToast("Kartı okutun veya UID girin");
			productSaleCardInput.focus();
			return;
		}
		var need = productSalePendingTotal;
		if (need <= 0) {
			showToast("Sepet tutarı geçersiz");
			return;
		}
		clearProductSaleCardIdle();
		productSaleCardSubmitting = true;
		if (productSaleCardConfirmBtn) {
			productSaleCardConfirmBtn.disabled = true;
		}
		resetProductSaleCardModalErrors();
		abortProductSaleCardFetch();
		var ac = new AbortController();
		productSaleCardAbort = ac;
		fetch("/api/cards/" + encodeURIComponent(uid) + "/detail", {
			headers: authHeaders(),
			signal: ac.signal,
		})
			.then(function (r) {
				if (r.status === 401) {
					window.location.replace("/index.html");
					return null;
				}
				return r.json().then(function (data) {
					return { ok: r.ok, status: r.status, data: data };
				});
			})
			.then(function (res) {
				if (!res) {
					productSaleCardSubmitting = false;
					if (productSaleCardConfirmBtn) {
						productSaleCardConfirmBtn.disabled = false;
					}
					return;
				}
				if (productSaleCardOverlay && productSaleCardOverlay.hidden) {
					return;
				}
				if (res.status === 404) {
					showToast("Kart bulunamadı");
					productSaleCardSubmitting = false;
					if (productSaleCardConfirmBtn) {
						productSaleCardConfirmBtn.disabled = false;
					}
					if (productSaleCardInput) {
						productSaleCardInput.focus();
					}
					return;
				}
				if (!res.ok) {
					showToast("Kart bilgisi alınamadı");
					productSaleCardSubmitting = false;
					if (productSaleCardConfirmBtn) {
						productSaleCardConfirmBtn.disabled = false;
					}
					if (productSaleCardInput) {
						productSaleCardInput.focus();
					}
					return;
				}
				var d = res.data || {};
				if (String(d.status || "").toUpperCase() !== "ACTIVE") {
					showToast("Kart kullanılamıyor (bloke veya pasif)");
					if (productSaleCardError) {
						productSaleCardError.textContent = "Bu kart satış için kullanılamıyor.";
						productSaleCardError.hidden = false;
					}
					productSaleCardSubmitting = false;
					if (productSaleCardConfirmBtn) {
						productSaleCardConfirmBtn.disabled = false;
					}
					if (productSaleCardInput) {
						productSaleCardInput.focus();
					}
					return;
				}
				var bal = Number(d.balance);
				if (isNaN(bal)) {
					bal = 0;
				}
				if (bal + 1e-9 < need) {
					var shortfall = need - bal;
					if (productSaleCardBalanceLine) {
						productSaleCardBalanceLine.textContent =
							"Bakiye: " + money(bal) + " · Sepet: " + money(need);
						productSaleCardBalanceLine.hidden = false;
					}
					if (productSaleCardError) {
						productSaleCardError.textContent =
							"Yetersiz bakiye. Eksik: " +
							money(shortfall) +
							" (Bakiye: " +
							money(bal) +
							" · Gerekli: " +
							money(need) +
							")";
						productSaleCardError.hidden = false;
					}
					showToast("Yetersiz bakiye");
					productSaleCardSubmitting = false;
					if (productSaleCardConfirmBtn) {
						productSaleCardConfirmBtn.disabled = false;
					}
					if (productSaleCardInput) {
						productSaleCardInput.focus();
					}
					return;
				}
				closeProductSaleCardModal();
				completeProductCart(uid);
			})
			.catch(function (err) {
				if (err && err.name === "AbortError") {
					return;
				}
				if (!productSaleCardOverlay || productSaleCardOverlay.hidden) {
					return;
				}
				showToast("Sunucuya bağlanılamadı");
				productSaleCardSubmitting = false;
				if (productSaleCardConfirmBtn) {
					productSaleCardConfirmBtn.disabled = false;
				}
				if (productSaleCardInput) {
					productSaleCardInput.focus();
				}
			})
			.finally(function () {
				if (productSaleCardAbort === ac) {
					productSaleCardAbort = null;
				}
			});
	}

	if (productSaleCardConfirmBtn) {
		productSaleCardConfirmBtn.addEventListener("click", function () {
			clearProductSaleCardIdle();
			confirmProductSaleCard();
		});
	}
	if (productSaleCardCancelBtn) {
		productSaleCardCancelBtn.addEventListener("click", function () {
			closeProductSaleCardModal();
		});
	}
	if (productSaleCardInput) {
		productSaleCardInput.addEventListener("input", function () {
			resetProductSaleCardModalErrors();
			scheduleProductSaleCardIdle();
		});
		productSaleCardInput.addEventListener("keydown", function (e) {
			if (e.key === "Enter") {
				e.preventDefault();
				clearProductSaleCardIdle();
				confirmProductSaleCard();
			}
		});
	}

	/** Kart sorgulama: SORGULA → modal → GET /api/cards/{uid}/detail */
	var sorguCardOverlay = document.getElementById("sorgu-card-overlay");
	var sorguCardScanWrap = document.getElementById("sorgu-card-scan-wrap");
	var sorguCardDetailWrap = document.getElementById("sorgu-card-detail-wrap");
	var sorguCardInput = document.getElementById("sorgu-card-input");
	var sorguCardConfirmBtn = document.getElementById("sorgu-card-confirm");
	var sorguCardCancelScanBtn = document.getElementById("sorgu-card-cancel-scan");
	var sorguCardAnotherBtn = document.getElementById("sorgu-card-another");
	var sorguCardCloseDetailBtn = document.getElementById("sorgu-card-close-detail");
	var sorguCardLoadingMsg = document.getElementById("sorgu-card-loading-msg");
	var sorguModalLedgerBody = document.getElementById("sorgu-modal-ledger-body");
	var sorguModalSubmitting = false;
	var sorguCardIdleTimer = null;
	var SORGU_CARD_IDLE_MS = 150;
	var SORGU_CARD_IDLE_MIN_LEN = 4;

	function clearSorguCardIdle() {
		if (sorguCardIdleTimer) {
			clearTimeout(sorguCardIdleTimer);
			sorguCardIdleTimer = null;
		}
	}

	function scheduleSorguCardIdle() {
		if (!sorguCardOverlay || sorguCardOverlay.hidden || !sorguCardInput) {
			return;
		}
		clearSorguCardIdle();
		sorguCardIdleTimer = setTimeout(function () {
			sorguCardIdleTimer = null;
			if (!sorguCardOverlay || sorguCardOverlay.hidden || sorguModalSubmitting) {
				return;
			}
			if (sorguCardDetailWrap && !sorguCardDetailWrap.hidden) {
				return;
			}
			var v = cleanUid(sorguCardInput.value);
			if (v.length >= SORGU_CARD_IDLE_MIN_LEN) {
				confirmSorguCardScan();
			}
		}, SORGU_CARD_IDLE_MS);
	}

	function resetSorguModalScanUi() {
		if (sorguCardDetailWrap) {
			sorguCardDetailWrap.hidden = true;
		}
		if (sorguCardScanWrap) {
			sorguCardScanWrap.hidden = false;
		}
		if (sorguCardLoadingMsg) {
			sorguCardLoadingMsg.hidden = true;
		}
		if (sorguModalLedgerBody) {
			renderUrunLedgerRows(sorguModalLedgerBody, []);
		}
	}

	function applySorguModalDetail(d) {
		var uidEl = document.getElementById("sorgu-modal-uid");
		var balEl = document.getElementById("sorgu-modal-balance");
		var stEl = document.getElementById("sorgu-modal-status");
		var loadEl = document.getElementById("sorgu-modal-loaded");
		var spentEl = document.getElementById("sorgu-modal-spent");
		if (uidEl) {
			uidEl.textContent = d.uid != null ? String(d.uid) : "—";
		}
		var bal = Number(d.balance);
		if (balEl) {
			balEl.textContent = typeof bal === "number" && !isNaN(bal) ? money(bal) : "—";
		}
		if (stEl) {
			stEl.textContent = statusTr(d.status);
		}
		var tl = d.totalLoaded != null ? Number(d.totalLoaded) : null;
		var ts = d.totalSpent != null ? Number(d.totalSpent) : null;
		if (loadEl) {
			loadEl.textContent = tl != null && !isNaN(tl) ? money(tl) : "—";
		}
		if (spentEl) {
			spentEl.textContent = ts != null && !isNaN(ts) ? money(ts) : "—";
		}
		var led = Array.isArray(d.ledger) ? d.ledger : [];
		renderUrunLedgerRows(sorguModalLedgerBody, led);
		if (sorguCardScanWrap) {
			sorguCardScanWrap.hidden = true;
		}
		if (sorguCardDetailWrap) {
			sorguCardDetailWrap.hidden = false;
		}
	}

	function fetchSorguCardDetail(uid) {
		var uidT = cleanUid(uid);
		if (!uidT.length) {
			showToast("Kartı okutun veya UID girin");
			return Promise.resolve(false);
		}
		if (sorguModalSubmitting) {
			return Promise.resolve(false);
		}
		sorguModalSubmitting = true;
		clearSorguCardIdle();
		if (sorguCardConfirmBtn) {
			sorguCardConfirmBtn.disabled = true;
		}
		if (sorguCardCancelScanBtn) {
			sorguCardCancelScanBtn.disabled = true;
		}
		if (sorguCardLoadingMsg) {
			sorguCardLoadingMsg.hidden = false;
		}
		return fetch("/api/cards/" + encodeURIComponent(uidT) + "/detail", { headers: authHeaders() })
			.then(function (r) {
				if (r.status === 401) {
					window.location.replace("/index.html");
					return null;
				}
				if (r.status === 404) {
					showToast("Kart bulunamadı");
					return null;
				}
				if (!r.ok) {
					showToast("Sorgu yapılamadı");
					return null;
				}
				return r.json();
			})
			.then(function (data) {
				if (!data) {
					return false;
				}
				applySorguModalDetail(data);
				showToast("Sorgu tamamlandı");
				return true;
			})
			.catch(function (err) {
				if (err && err.name === "TypeError") {
					showToast("Sunucuya bağlanılamadı");
					return false;
				}
				showToast("Sorgu başarısız");
				return false;
			})
			.finally(function () {
				sorguModalSubmitting = false;
				if (sorguCardConfirmBtn) {
					sorguCardConfirmBtn.disabled = false;
				}
				if (sorguCardCancelScanBtn) {
					sorguCardCancelScanBtn.disabled = false;
				}
				if (sorguCardLoadingMsg) {
					sorguCardLoadingMsg.hidden = true;
				}
			});
	}

	function confirmSorguCardScan() {
		if (!sorguCardInput || sorguModalSubmitting) {
			return;
		}
		if (sorguCardDetailWrap && !sorguCardDetailWrap.hidden) {
			return;
		}
		var uid = sorguCardInput.value;
		if (!cleanUid(uid).length) {
			showToast("Kartı okutun veya UID girin");
			sorguCardInput.focus();
			return;
		}
		clearSorguCardIdle();
		fetchSorguCardDetail(uid);
	}

	function openSorguInquiryModal() {
		if (!sorguCardOverlay || !sorguCardInput) {
			return;
		}
		clearSorguCardIdle();
		sorguModalSubmitting = false;
		resetSorguModalScanUi();
		var pre = sorguDigits.trim();
		sorguCardInput.value = pre;
		sorguCardOverlay.hidden = false;
		sorguCardOverlay.setAttribute("aria-hidden", "false");
		setTimeout(function () {
			sorguCardInput.focus();
		}, 30);
		setTimeout(function () {
			sorguCardInput.focus();
		}, 120);
	}

	function closeSorguInquiryModal() {
		clearSorguCardIdle();
		sorguModalSubmitting = false;
		if (sorguCardOverlay) {
			blurFocusInsideOverlay(sorguCardOverlay);
			sorguCardOverlay.hidden = true;
			sorguCardOverlay.setAttribute("aria-hidden", "true");
		}
		if (sorguCardInput) {
			sorguCardInput.value = "";
			try {
				sorguCardInput.blur();
			} catch (e) {}
		}
		resetSorguModalScanUi();
	}

	function onSorguAnotherCard() {
		if (!sorguCardInput) {
			return;
		}
		clearSorguCardIdle();
		sorguModalSubmitting = false;
		sorguCardInput.value = "";
		resetSorguModalScanUi();
		setTimeout(function () {
			sorguCardInput.focus();
		}, 30);
	}

	if (sorguCardConfirmBtn) {
		sorguCardConfirmBtn.addEventListener("click", function () {
			clearSorguCardIdle();
			confirmSorguCardScan();
		});
	}
	if (sorguCardCancelScanBtn) {
		sorguCardCancelScanBtn.addEventListener("click", function () {
			closeSorguInquiryModal();
		});
	}
	if (sorguCardAnotherBtn) {
		sorguCardAnotherBtn.addEventListener("click", function () {
			onSorguAnotherCard();
		});
	}
	if (sorguCardCloseDetailBtn) {
		sorguCardCloseDetailBtn.addEventListener("click", function () {
			closeSorguInquiryModal();
		});
	}
	if (sorguCardInput) {
		sorguCardInput.addEventListener("input", scheduleSorguCardIdle);
		sorguCardInput.addEventListener("keydown", function (e) {
			if (e.key === "Enter") {
				e.preventDefault();
				clearSorguCardIdle();
				confirmSorguCardScan();
			}
		});
	}

	/** Ürün satış: Kartı yükle → modal (HID) */
	var urunCardLoadOverlay = document.getElementById("urun-card-load-overlay");
	var urunCardLoadInput = document.getElementById("urun-card-load-input");
	var urunCardLoadConfirmBtn = document.getElementById("urun-card-load-confirm");
	var urunCardLoadCancelBtn = document.getElementById("urun-card-load-cancel");
	var urunCardLoadSubmitting = false;
	var urunCardLoadIdleTimer = null;
	var URUN_CARD_LOAD_IDLE_MS = 150;
	var URUN_CARD_LOAD_IDLE_MIN_LEN = 4;

	function clearUrunCardLoadIdle() {
		if (urunCardLoadIdleTimer) {
			clearTimeout(urunCardLoadIdleTimer);
			urunCardLoadIdleTimer = null;
		}
	}

	function scheduleUrunCardLoadIdle() {
		if (!urunCardLoadOverlay || urunCardLoadOverlay.hidden || !urunCardLoadInput) {
			return;
		}
		clearUrunCardLoadIdle();
		urunCardLoadIdleTimer = setTimeout(function () {
			urunCardLoadIdleTimer = null;
			if (!urunCardLoadOverlay || urunCardLoadOverlay.hidden || urunCardLoadSubmitting) {
				return;
			}
			var v = cleanUid(urunCardLoadInput.value);
			if (v.length >= URUN_CARD_LOAD_IDLE_MIN_LEN) {
				confirmUrunCardLoad();
			}
		}, URUN_CARD_LOAD_IDLE_MS);
	}

	function openUrunCardLoadModal() {
		if (!urunCardLoadOverlay || !urunCardLoadInput) {
			return;
		}
		clearUrunCardLoadIdle();
		urunCardLoadSubmitting = false;
		if (urunCardLoadConfirmBtn) {
			urunCardLoadConfirmBtn.disabled = false;
		}
		if (urunCardLoadCancelBtn) {
			urunCardLoadCancelBtn.disabled = false;
		}
		var foot = document.getElementById("urun-card-input");
		urunCardLoadInput.value = foot ? cleanUid(foot.value) : "";
		urunCardLoadOverlay.hidden = false;
		urunCardLoadOverlay.setAttribute("aria-hidden", "false");
		setTimeout(function () {
			urunCardLoadInput.focus();
		}, 30);
		setTimeout(function () {
			urunCardLoadInput.focus();
		}, 120);
	}

	function closeUrunCardLoadModal() {
		clearUrunCardLoadIdle();
		urunCardLoadSubmitting = false;
		if (urunCardLoadOverlay) {
			blurFocusInsideOverlay(urunCardLoadOverlay);
			urunCardLoadOverlay.hidden = true;
			urunCardLoadOverlay.setAttribute("aria-hidden", "true");
		}
		if (urunCardLoadInput) {
			urunCardLoadInput.value = "";
			try {
				urunCardLoadInput.blur();
			} catch (e) {}
		}
		if (urunCardLoadConfirmBtn) {
			urunCardLoadConfirmBtn.disabled = false;
		}
		if (urunCardLoadCancelBtn) {
			urunCardLoadCancelBtn.disabled = false;
		}
	}

	function confirmUrunCardLoad() {
		if (!urunCardLoadInput || urunCardLoadSubmitting) {
			return;
		}
		var uid = urunCardLoadInput.value;
		if (!cleanUid(uid).length) {
			showToast("Kartı okutun veya UID girin");
			urunCardLoadInput.focus();
			return;
		}
		clearUrunCardLoadIdle();
		urunCardLoadSubmitting = true;
		if (urunCardLoadConfirmBtn) {
			urunCardLoadConfirmBtn.disabled = true;
		}
		if (urunCardLoadCancelBtn) {
			urunCardLoadCancelBtn.disabled = true;
		}
		var v = cleanUid(uid);
		var inp = document.getElementById("urun-card-input");
		if (inp) {
			inp.readOnly = false;
			inp.value = v;
		}
		urunCardUid = v;
		loadUrunCardDetail(v).then(function (ok) {
			urunCardLoadSubmitting = false;
			if (urunCardLoadConfirmBtn) {
				urunCardLoadConfirmBtn.disabled = false;
			}
			if (urunCardLoadCancelBtn) {
				urunCardLoadCancelBtn.disabled = false;
			}
			if (ok) {
				closeUrunCardLoadModal();
			} else if (urunCardLoadInput) {
				urunCardLoadInput.focus();
			}
		});
	}

	if (urunCardLoadConfirmBtn) {
		urunCardLoadConfirmBtn.addEventListener("click", function () {
			clearUrunCardLoadIdle();
			confirmUrunCardLoad();
		});
	}
	if (urunCardLoadCancelBtn) {
		urunCardLoadCancelBtn.addEventListener("click", function () {
			closeUrunCardLoadModal();
		});
	}
	if (urunCardLoadInput) {
		urunCardLoadInput.addEventListener("input", scheduleUrunCardLoadIdle);
		urunCardLoadInput.addEventListener("keydown", function (e) {
			if (e.key === "Enter") {
				e.preventDefault();
				clearUrunCardLoadIdle();
				confirmUrunCardLoad();
			}
		});
	}

	document.addEventListener("keydown", function (e) {
		if (e.key !== "Escape") {
			return;
		}
		if (bakiyeCardBindOverlay && !bakiyeCardBindOverlay.hidden) {
			e.preventDefault();
			closeBakiyeCardBindModal();
			return;
		}
		if (sorguCardOverlay && !sorguCardOverlay.hidden) {
			e.preventDefault();
			closeSorguInquiryModal();
			return;
		}
		if (urunCardLoadOverlay && !urunCardLoadOverlay.hidden) {
			e.preventDefault();
			closeUrunCardLoadModal();
			return;
		}
		if (!ticketCardBindOverlay || ticketCardBindOverlay.hidden) {
			return;
		}
		e.preventDefault();
		closeTicketCardBindModal();
	});

	function cleanUid(s) {
		return String(s || "")
			.trim()
			.replace(/\s+/g, "");
	}

	/**
	 * Seri okuyucular çoğu zaman UTF-8 metin değil ham bayt (4–10 byte UID) gönderir.
	 * Yazdırılabilir ASCII ise metin; değilse hex (backend UID string olarak kullanılır).
	 */
	function uidFromSerialBytes(bytes) {
		if (!bytes || bytes.length === 0) {
			return "";
		}
		var i = 0;
		var j = bytes.length;
		while (i < j && (bytes[i] === 32 || bytes[i] === 9)) {
			i++;
		}
		while (j > i && (bytes[j - 1] === 32 || bytes[j - 1] === 9)) {
			j--;
		}
		var slice = bytes.subarray(i, j);
		if (slice.length < 4) {
			return "";
		}
		var allPrint = true;
		var s = "";
		for (var k = 0; k < slice.length; k++) {
			var b = slice[k];
			if (b >= 32 && b <= 126) {
				s += String.fromCharCode(b);
			} else {
				allPrint = false;
				break;
			}
		}
		if (allPrint && s.length >= 4) {
			return cleanUid(s);
		}
		var hex = "";
		for (var m = 0; m < slice.length; m++) {
			hex += slice[m].toString(16).padStart(2, "0");
		}
		return hex.toUpperCase();
	}

	function setRfidOverlayMessage(msg) {
		if (rfidMsgEl) rfidMsgEl.textContent = msg;
	}

	function showRfidOverlay(title, mode) {
		if (!rfidOverlayEl) return;
		var t = document.getElementById("rfid-read-title");
		if (t && title) t.textContent = title;
		rfidOverlayEl.hidden = false;
		rfidOverlayEl.setAttribute("aria-hidden", "false");
		var panel = rfidOverlayEl.querySelector(".pos-rfid-overlay__panel");
		if (panel) {
			panel.classList.toggle("pos-rfid-panel--hid", mode === "hid");
		}
		if (rfidInputEl) {
			if (mode === "hid") {
				rfidInputEl.removeAttribute("hidden");
				rfidInputEl.value = "";
			} else {
				rfidInputEl.setAttribute("hidden", "");
				rfidInputEl.value = "";
			}
		}
	}

	function hideRfidOverlay() {
		if (!rfidOverlayEl) return;
		blurFocusInsideOverlay(rfidOverlayEl);
		rfidOverlayEl.hidden = true;
		rfidOverlayEl.setAttribute("aria-hidden", "true");
		var panel = rfidOverlayEl.querySelector(".pos-rfid-overlay__panel");
		if (panel) {
			panel.classList.remove("pos-rfid-panel--hid");
		}
		if (rfidInputEl) {
			rfidInputEl.setAttribute("hidden", "");
			rfidInputEl.value = "";
			rfidInputEl.blur();
		}
	}

	function pickSerialPort() {
		return navigator.serial.getPorts().then(function (ports) {
			if (ports.length === 1) return ports[0];
			return navigator.serial.requestPort();
		});
	}

	function readUidFromSerialStream(port, signal) {
		var reader = port.readable.getReader();
		var buf = new Uint8Array(0);
		var idleTimer = null;
		var settled = false;
		var SERIAL_IDLE_MS = 650;

		function appendChunk(chunk) {
			if (!chunk || chunk.length === 0) return;
			var u8 = chunk instanceof Uint8Array ? chunk : new Uint8Array(chunk);
			var next = new Uint8Array(buf.length + u8.length);
			next.set(buf, 0);
			next.set(u8, buf.length);
			buf = next;
		}

		function clearIdle() {
			if (idleTimer) {
				clearTimeout(idleTimer);
				idleTimer = null;
			}
		}

		function releaseReader() {
			return reader.releaseLock().catch(function () {});
		}

		function doneResolve(resolve, reject, uidStr) {
			if (settled) return;
			settled = true;
			clearIdle();
			buf = new Uint8Array(0);
			releaseReader().then(function () {
				var u = cleanUid(uidStr);
				if (u.length >= 4) resolve(u);
				else reject(new Error("EMPTY_UID"));
			});
		}

		function tryConsumeLine(resolve, reject) {
			while (buf.length > 0) {
				var i;
				for (i = 0; i < buf.length; i++) {
					if (buf[i] === 10 || buf[i] === 13) break;
				}
				if (i >= buf.length) {
					return false;
				}
				var c = buf[i];
				var line = buf.subarray(0, i);
				var skip = 1;
				if (c === 13 && i + 1 < buf.length && buf[i + 1] === 10) {
					skip = 2;
				}
				buf = buf.slice(i + skip);
				var uid = uidFromSerialBytes(line);
				if (uid.length >= 4) {
					doneResolve(resolve, reject, uid);
					return true;
				}
			}
			return false;
		}

		function flushIdle(resolve, reject) {
			if (settled) return;
			var uid = uidFromSerialBytes(buf);
			if (uid.length >= 4) {
				doneResolve(resolve, reject, uid);
			}
		}

		function resetIdle(resolve, reject) {
			clearIdle();
			idleTimer = setTimeout(function () {
				flushIdle(resolve, reject);
			}, SERIAL_IDLE_MS);
		}

		return new Promise(function (resolve, reject) {
			function onAbort() {
				if (settled) return;
				settled = true;
				clearIdle();
				buf = new Uint8Array(0);
				releaseReader().then(function () {
					reject(new Error("cancel"));
				});
			}
			if (signal) signal.addEventListener("abort", onAbort);

			(async function loop() {
				try {
					while (!settled) {
						var r = await reader.read();
						if (r.done) break;
						if (signal && signal.aborted) return;
						appendChunk(r.value);
						if (tryConsumeLine(resolve, reject)) return;
						resetIdle(resolve, reject);
					}
					if (!settled) {
						var uid = uidFromSerialBytes(buf);
						if (uid.length >= 4) {
							doneResolve(resolve, reject, uid);
						} else {
							settled = true;
							clearIdle();
							releaseReader().then(function () {
								reject(new Error("EMPTY_UID"));
							});
						}
					}
				} catch (e) {
					if (!settled) {
						settled = true;
						clearIdle();
						releaseReader().then(function () {
							reject(e);
						});
					}
				}
			})();
		});
	}

	function readCardUidViaSerial(signal) {
		setRfidOverlayMessage("USB port seçin veya onaylayın…");
		return pickSerialPort()
			.catch(function (e) {
				if (e && e.name === "NotFoundError") {
					throw new Error("PORT_CANCEL");
				}
				throw e;
			})
			.then(function (port) {
				if (signal.aborted) throw new Error("cancel");
				setRfidOverlayMessage("Okuyucuya bağlanılıyor…");
				var baud = parseInt(sessionStorage.getItem("aqua_rfid_baud") || "9600", 10);
				if (isNaN(baud) || baud < 300) baud = 9600;
				return port.open({ baudRate: baud }).then(function () {
					return port;
				});
			})
			.catch(function (e) {
				if (e && e.message === "cancel") throw e;
				if (e && e.message === "PORT_CANCEL") throw e;
				throw new Error("CONNECTION_FAILED");
			})
			.then(function (port) {
				if (signal.aborted) {
					return port.close().catch(function () {}).then(function () {
						throw new Error("cancel");
					});
				}
				signal.addEventListener(
					"abort",
					function () {
						port.close().catch(function () {});
					},
					{ once: true }
				);
				setRfidOverlayMessage("Kartı okutun. Veri gelmezse «Klavye modu» veya baud 115200 (aqua_rfid_baud).");
				return readUidFromSerialStream(port, signal).finally(function () {
					return port.close().catch(function () {});
				});
			});
	}

	function readCardUidViaHid(signal) {
		setRfidOverlayMessage(
			"Kartı okutun. İmleç bu penceredeyken okuyucunun yazdığı rakamlar buraya düşmeli (klavye HID modu)."
		);
		if (rfidInputEl) {
			rfidInputEl.removeAttribute("hidden");
			rfidInputEl.setAttribute("tabindex", "0");
			rfidInputEl.value = "";
		}
		return new Promise(function (resolve, reject) {
			var input = rfidInputEl;
			if (!input) {
				reject(new Error("CONNECTION_FAILED"));
				return;
			}
			var settled = false;
			var idleTimer = null;
			var longTimeout = setTimeout(function () {
				if (settled) return;
				settled = true;
				cleanup();
				reject(new Error("timeout"));
			}, 60000);

			function focusInput() {
				try {
					input.focus({ preventScroll: true });
				} catch (e) {
					input.focus();
				}
			}

			function cleanup() {
				clearTimeout(longTimeout);
				if (idleTimer) clearTimeout(idleTimer);
				input.removeEventListener("keydown", onKey);
				input.removeEventListener("input", onIn);
				if (signal) signal.removeEventListener("abort", onAbort);
				if (rfidOverlayEl) {
					rfidOverlayEl.removeEventListener("pointerdown", onOverlayPointer, true);
				}
			}

			function onOverlayPointer(e) {
				if (e.target === input || input.contains(e.target)) return;
				if (e.target && e.target.closest && e.target.closest("#rfid-read-cancel")) return;
				if (e.target && e.target.closest && e.target.closest("#rfid-use-hid")) return;
				e.preventDefault();
				focusInput();
			}

			function onAbort() {
				if (settled) return;
				settled = true;
				cleanup();
				reject(new Error("cancel"));
			}

			function tryResolve() {
				var v = cleanUid(input.value);
				if (v.length >= 4) {
					if (settled) return;
					settled = true;
					cleanup();
					resolve(v);
				}
			}

			function onIn() {
				if (idleTimer) clearTimeout(idleTimer);
				idleTimer = setTimeout(tryResolve, 140);
			}

			function onKey(e) {
				if (e.key === "Enter") {
					e.preventDefault();
					tryResolve();
					if (!settled && cleanUid(input.value).length > 0 && cleanUid(input.value).length < 4) {
						showToast("Kart numarası çok kısa");
					}
				}
			}

			if (signal) signal.addEventListener("abort", onAbort);
			input.addEventListener("input", onIn);
			input.addEventListener("keydown", onKey);
			if (rfidOverlayEl) {
				rfidOverlayEl.addEventListener("pointerdown", onOverlayPointer, true);
			}
			requestAnimationFrame(function () {
				focusInput();
				setTimeout(focusInput, 50);
				setTimeout(focusInput, 200);
			});
		});
	}

	function readCardUidFromReader() {
		if (rfidReadInProgress) {
			return Promise.reject(new Error("busy"));
		}
		rfidReadInProgress = true;
		rfidReadAbort = new AbortController();
		var signal = rfidReadAbort.signal;
		var forceHid = sessionStorage.getItem("aqua_rfid_force_hid") === "1";
		var useSerial = !forceHid && typeof navigator !== "undefined" && navigator.serial;
		if (useSerial) {
			showRfidOverlay("Kart okuyucu", "serial");
		} else {
			showRfidOverlay("Kart okuyucu", "hid");
		}
		var p = useSerial ? readCardUidViaSerial(signal) : readCardUidViaHid(signal);
		return p.finally(function () {
			rfidReadInProgress = false;
			rfidReadAbort = null;
			hideRfidOverlay();
		});
	}

	function rfidUserMessage(err) {
		var m = err && err.message;
		if (m === "cancel" || m === "busy") return "";
		if (m === "PORT_CANCEL") return "Port seçilmedi — satış tamamlanmadı";
		if (m === "CONNECTION_FAILED") return "Kart okuyucuya bağlanılamadı — satış tamamlanmadı";
		if (m === "EMPTY_UID") return "Kart okunamadı — satış tamamlanmadı";
		if (m === "timeout") return "Süre doldu — satış tamamlanmadı";
		return "Kart okunamadı — satış tamamlanmadı";
	}

	var btnRfidCancel = document.getElementById("rfid-read-cancel");
	if (btnRfidCancel) {
		btnRfidCancel.addEventListener("click", function () {
			if (rfidReadAbort) rfidReadAbort.abort();
		});
	}
	var btnRfidUseHid = document.getElementById("rfid-use-hid");
	if (btnRfidUseHid) {
		btnRfidUseHid.addEventListener("click", function () {
			sessionStorage.setItem("aqua_rfid_force_hid", "1");
			showToast("Klavye modu seçildi — tekrar «Satışı tamamla»ya basın.");
			if (rfidReadAbort) rfidReadAbort.abort();
		});
	}

	function setActiveNav(module) {
		document.querySelectorAll(".nav-item[data-module]").forEach(function (b) {
			var m = b.getAttribute("data-module");
			var preset = b.getAttribute("data-preset-area");
			var on = false;
			if (module === "urun") {
				on = m === "urun" && !preset;
			} else {
				on = m === module;
			}
			b.classList.toggle("active", on);
		});
	}

	function updateContextBar() {
		var badge = document.getElementById("pos-context-badge");
		var hint = document.getElementById("pos-context-hint");
		var mk = document.getElementById("pos-context-metrics-kart");
		var mb = document.getElementById("pos-context-metrics-bakiye");
		var elItems = document.getElementById("pos-metric-items");
		var elDue = document.getElementById("pos-metric-due");
		var elBak = document.getElementById("pos-metric-bakiye");
		if (!badge || !hint) {
			return;
		}
		if (mk) {
			mk.hidden = true;
		}
		if (mb) {
			mb.hidden = true;
		}
		var m = currentModule;
		if (m === "kart") {
			badge.textContent = getKartBadgeLabel();
			hint.textContent = getKartBadgeHint();
			if (mk && elItems && elDue) {
				mk.hidden = false;
				elItems.textContent = String(cart.length);
				var sub = subtotal();
				var disc = effectiveDiscount();
				var due = Math.max(0, sub - sub * disc);
				elDue.textContent = money(due);
			}
		} else if (m === "bakiye") {
			badge.textContent = "Bakiye yükleme";
			hint.textContent = "Tutarı girin, ödeme yöntemini seçin; Yüklemeyi tamamla ile kartı okutun.";
			if (mb && elBak) {
				mb.hidden = false;
				elBak.textContent = money(keypadValue / 100);
			}
		} else if (m === "urun") {
			badge.textContent = "Ürün satış";
			hint.textContent = "Kartı yükleyin; özet ve son işlemler sağda.";
		} else if (m === "sorgu") {
			badge.textContent = "Kart sorgulama";
			hint.textContent = "Sorgula ile kartı okutun; hareketler sunucudan gelir.";
		}
	}

	function setModule(m) {
		currentModule = m;
		setActiveNav(m);
		viewKart.hidden = m !== "kart";
		viewBakiye.hidden = m !== "bakiye";
		viewSorgu.hidden = m !== "sorgu";
		viewUrun.hidden = m !== "urun";
		document.body.classList.toggle("module-kart", m === "kart");
		document.body.classList.toggle("module-bakiye", m === "bakiye");
		document.body.classList.toggle("module-sorgu", m === "sorgu");
		document.body.classList.toggle("module-urun", m === "urun");
		if (footerPrimaryLabel) {
			if (m === "bakiye") {
				footerPrimaryLabel.textContent = "Yüklemeyi tamamla";
			} else if (m === "urun") {
				footerPrimaryLabel.textContent = "";
			} else if (m === "sorgu") {
				footerPrimaryLabel.textContent = "SORGULA";
			} else {
				footerPrimaryLabel.textContent = "Satışı tamamla";
			}
		}
		if (m === "bakiye") {
			updateBakiyeDisplay();
			updateBakiyeSummary();
		}
		if (m === "sorgu") {
			updateSorguDisplay();
		}
		if (m === "urun") {
			if (urunCardUid) {
				loadUrunCardDetail(urunCardUid);
			}
		}
		var pendingKartProductLoad = false;
		if (m === "kart" && kartMode === "products" && kartProductAreaCode) {
			if (kartCacheArea !== kartProductAreaCode) {
				pendingKartProductLoad = true;
				loadKartProductsForArea(kartProductAreaCode, function () {
					renderGrid();
					updateSummary();
					syncKartViewUi();
					updateKartNavLabel();
					var btnCode2 = document.getElementById("btn-code");
					if (btnCode2) {
						btnCode2.style.display = kartMode === "tickets" ? "" : "none";
					}
					updateContextBar();
				});
			} else {
				renderGrid();
				updateSummary();
			}
		}
		var btnCode = document.getElementById("btn-code");
		if (btnCode) {
			btnCode.style.display = m === "kart" && kartMode === "tickets" ? "" : "none";
		}
		if (!pendingKartProductLoad) {
			updateContextBar();
		}
	}

	function fmtDate(iso) {
		if (!iso) {
			return "—";
		}
		try {
			var d = new Date(iso);
			return d.toLocaleString("tr-TR");
		} catch (e) {
			return "—";
		}
	}

	function normalizeLedgerRow(row) {
		if (!row || typeof row !== "object") {
			return null;
		}
		var ac = row.amountChange != null ? row.amountChange : row.amount_change;
		var ba = row.balanceAfter != null ? row.balanceAfter : row.balance_after;
		var loc = row.saleAreaName != null ? row.saleAreaName : row.sale_area_name;
		var when = row.createdAt != null ? row.createdAt : row.created_at;
		return {
			createdAt: when,
			amountChange: Number(ac),
			balanceAfter: Number(ba),
			saleAreaName: loc,
			description: row.description,
			type: row.type,
		};
	}

	function ledgerDirectionLabel(amt, type) {
		var t = (type || "").toUpperCase();
		if (t === "SALE") {
			return "Çıkış";
		}
		if (t === "LOAD_CASH" || t === "LOAD_CARD" || t === "LOAD_AGENCY") {
			return "Giriş";
		}
		if (t === "REFUND_CASH") {
			return "İade";
		}
		if (t === "ENTRY") {
			return amt > 0 ? "Giriş" : "Geçiş";
		}
		if (amt > 0) {
			return "Giriş";
		}
		if (amt < 0) {
			return "Çıkış";
		}
		return "—";
	}

	function setUrunText(id, v) {
		var el = document.getElementById(id);
		if (!el) {
			return;
		}
		el.textContent = v != null && String(v).trim() !== "" ? String(v) : "—";
	}

	function setUrunMoney(id, n) {
		var el = document.getElementById(id);
		if (!el) {
			return;
		}
		if (n == null || n === "" || (typeof n === "number" && isNaN(n))) {
			el.textContent = "—";
			return;
		}
		el.textContent = money(Number(n));
	}

	function renderUrunLedgerRows(tb, rows) {
		if (!tb) {
			return;
		}
		tb.innerHTML = "";
		if (!rows || rows.length === 0) {
			var empty = document.createElement("tr");
			empty.innerHTML =
				'<td colspan="4" class="urun-ledger-empty">Bu kart için kayıtlı işlem yok.</td>';
			tb.appendChild(empty);
			return;
		}
		var list = rows
			.map(function (row) {
				return normalizeLedgerRow(row);
			})
			.filter(function (x) {
				return x != null;
			});
		list.sort(function (a, b) {
			var ta = a.createdAt ? new Date(a.createdAt).getTime() : 0;
			var tb = b.createdAt ? new Date(b.createdAt).getTime() : 0;
			return ta - tb;
		});
		list.forEach(function (r) {
			var amt = r.amountChange;
			if (typeof amt !== "number" || isNaN(amt)) {
				amt = 0;
			}
			var balAfter = r.balanceAfter;
			if (typeof balAfter !== "number" || isNaN(balAfter)) {
				balAfter = 0;
			}
			var tr = document.createElement("tr");
			var dirLabel = ledgerDirectionLabel(amt, r.type);
			var dirClass =
				amt > 0 ? "urun-td-dir-in" : amt < 0 ? "urun-td-dir-out" : "urun-td-dir-neutral";
			var loc = r.saleAreaName || "—";
			var descBase = r.description && String(r.description).trim();
			var extra =
				" · Tutar: " + money(Math.abs(amt)) + " · Yeni bakiye: " + money(balAfter);
			var statusText;
			if (!descBase) {
				statusText = (r.type || "") + extra;
			} else {
				var low = descBase.toLowerCase();
				var hasAmountDetail =
					low.includes("yeni bakiye") ||
					low.includes("yuklenen") ||
					low.includes("tutar:") ||
					low.includes("harcanan") ||
					low.includes("kalan bakiye");
				var hasMoneyMove = Math.abs(amt) > 1e-9;
				if (hasAmountDetail || !hasMoneyMove) {
					statusText = descBase;
				} else {
					statusText = descBase + extra;
				}
			}
			tr.innerHTML =
				'<td class="urun-td-mono">' +
				fmtDate(r.createdAt) +
				"</td>" +
				"<td>" +
				escapeHtml(loc) +
				'</td><td class="' +
				dirClass +
				'">' +
				escapeHtml(dirLabel) +
				"</td>" +
				"<td>" +
				escapeHtml(statusText) +
				"</td>";
			tb.appendChild(tr);
		});
	}

	/** API olmadan demo kartlar — DB yokken varsayılan bakiye */
	const URUN_OFFLINE_DEMO_UID = "123";
	const URUN_OFFLINE_DEMO_BALANCE = 5000;

	function clearUrunCardPanel(showToastMsg) {
		urunCardUid = "";
		var inp = document.getElementById("urun-card-input");
		if (inp) {
			inp.value = "";
			inp.readOnly = false;
		}
		[
			"urun-meta-serial",
			"urun-meta-chip",
			"urun-meta-created",
			"urun-meta-valid-from",
			"urun-meta-valid-to",
			"urun-meta-defined-by",
			"urun-meta-booth",
			"urun-meta-tariff",
		].forEach(function (id) {
			setUrunText(id, "");
		});
		[
			"urun-fin-cash",
			"urun-fin-cc",
			"urun-fin-deposit",
			"urun-fin-grand",
			"urun-fin-loaded",
			"urun-fin-spent",
			"urun-fin-refund",
			"urun-fin-expected",
			"urun-fin-balance",
		].forEach(function (id) {
			setUrunMoney(id, null);
		});
		renderUrunLedgerRows(document.getElementById("urun-ledger-body"), []);
		if (showToastMsg) {
			showToast("Kart alanı temizlendi");
		}
	}

	function applyUrunCardDetail(d) {
		urunCardUid = d.uid;
		var inp = document.getElementById("urun-card-input");
		if (inp) {
			inp.value = d.uid || "";
			inp.readOnly = true;
		}
		setUrunText("urun-meta-serial", d.cardId != null ? String(d.cardId) : "");
		setUrunText("urun-meta-chip", d.uid || "");
		setUrunText("urun-meta-created", d.createdAt ? fmtDate(d.createdAt) : "");
		setUrunText("urun-meta-valid-from", d.validFrom != null ? fmtDate(d.validFrom) : "");
		setUrunText("urun-meta-valid-to", d.validTo != null ? fmtDate(d.validTo) : "");
		setUrunText("urun-meta-defined-by", d.definedBy);
		setUrunText("urun-meta-booth", d.booth);
		setUrunText("urun-meta-tariff", d.tariff);

		var bal = Number(d.balance);
		if (typeof bal !== "number" || isNaN(bal)) {
			bal = 0;
		}
		var loaded = Number(d.totalLoaded);
		if (typeof loaded !== "number" || isNaN(loaded)) {
			loaded = 0;
		}
		var spent = Number(d.totalSpent);
		if (typeof spent !== "number" || isNaN(spent)) {
			spent = 0;
		}

		setUrunMoney("urun-fin-cash", d.cashTotal != null ? Number(d.cashTotal) : 0);
		setUrunMoney("urun-fin-cc", d.cardTotal != null ? Number(d.cardTotal) : null);
		setUrunMoney("urun-fin-deposit", d.depositTotal != null ? Number(d.depositTotal) : null);
		setUrunMoney("urun-fin-grand", d.grandTotal != null ? Number(d.grandTotal) : null);
		setUrunMoney("urun-fin-loaded", loaded);
		setUrunMoney("urun-fin-spent", spent);
		setUrunMoney("urun-fin-refund", d.refundTotal != null ? Number(d.refundTotal) : 0);
		setUrunMoney("urun-fin-expected", d.expectedBalance != null ? Number(d.expectedBalance) : bal);
		setUrunMoney("urun-fin-balance", bal);

		var tb = document.getElementById("urun-ledger-body");
		var apiLedger = Array.isArray(d.ledger) ? d.ledger : [];
		renderUrunLedgerRows(tb, apiLedger);
	}

	/** @returns {Promise<boolean>} başarılı yükleme ise true */
	function loadUrunCardDetail(uid) {
		var key = uid != null ? String(uid).trim() : "";
		if (!key) {
			return Promise.resolve(false);
		}
		if (key === URUN_OFFLINE_DEMO_UID) {
			applyUrunCardDetail({
				uid: "123",
				cardId: 1,
				balance: URUN_OFFLINE_DEMO_BALANCE,
				status: "ACTIVE",
				createdAt: "2026-04-05T10:00:00.000+03:00",
				totalLoaded: URUN_OFFLINE_DEMO_BALANCE,
				totalSpent: 0,
				cashTotal: 0,
				cardTotal: null,
				depositTotal: null,
				grandTotal: null,
				refundTotal: 0,
				expectedBalance: URUN_OFFLINE_DEMO_BALANCE,
				ledger: [],
			});
			showToast("Kart yüklendi");
			return Promise.resolve(true);
		}
		if (key === "1234") {
			applyUrunCardDetail({
				uid: "1234",
				cardId: 0,
				balance: 0,
				status: "ACTIVE",
				createdAt: "2025-09-05T10:53:51.000+03:00",
				validFrom: "2025-09-05T00:00:00.000+03:00",
				validTo: "2025-09-06T00:00:00.000+03:00",
				definedBy: "0003 — Aslı ARPAZLI",
				booth: "GİŞE-03",
				tariff: "7–12 Yaş",
				totalLoaded: 1200,
				totalSpent: 1200,
				cashTotal: 0,
				cardTotal: 1250,
				depositTotal: 1250,
				grandTotal: 1250,
				refundTotal: 0,
				expectedBalance: 0,
				ledger: [
					{
						createdAt: "2025-09-05T10:53:51.000+03:00",
						amountChange: 0,
						balanceAfter: 0,
						saleAreaName: "GİŞE-03",
						description: "Kart tanımlandı. (7–12 Yaş)",
						type: "ENTRY",
					},
					{
						createdAt: "2025-09-05T11:55:28.000+03:00",
						amountChange: 0,
						balanceAfter: 0,
						saleAreaName: "GİRİŞ-2",
						description: "Giriş — Geçiş izni verildi.",
						type: "ENTRY",
					},
					{
						createdAt: "2025-09-05T13:59:40.000+03:00",
						amountChange: 500,
						balanceAfter: 500,
						saleAreaName: "FIRIN",
						description:
							"500,00 TL bakiye yüklendi. Yeni bakiye: 500,00 TL (Nakit: 500,00 TL)",
						type: "LOAD_CASH",
					},
					{
						createdAt: "2025-09-05T14:00:15.000+03:00",
						amountChange: 500,
						balanceAfter: 1000,
						saleAreaName: "FIRIN",
						description:
							"500,00 TL bakiye yüklendi. Yeni bakiye: 1.000,00 TL (Nakit: 500,00 TL)",
						type: "LOAD_CASH",
					},
					{
						createdAt: "2025-09-05T14:01:02.000+03:00",
						amountChange: 200,
						balanceAfter: 1200,
						saleAreaName: "FIRIN",
						description:
							"200,00 TL bakiye yüklendi. Yeni bakiye: 1.200,00 TL (Nakit: 200,00 TL)",
						type: "LOAD_CASH",
					},
					{
						createdAt: "2025-09-05T14:05:40.000+03:00",
						amountChange: -360,
						balanceAfter: 840,
						saleAreaName: "SUN SHINE",
						description: "360,00 TL harcama (kafeterya) yapıldı. Yeni bakiye: 840,00 TL",
						type: "SALE",
					},
					{
						createdAt: "2025-09-05T14:30:12.000+03:00",
						amountChange: -700,
						balanceAfter: 140,
						saleAreaName: "SUN SHINE",
						description: "700,00 TL harcama (kafeterya) yapıldı. Yeni bakiye: 140,00 TL",
						type: "SALE",
					},
					{
						createdAt: "2025-09-05T14:45:19.000+03:00",
						amountChange: -140,
						balanceAfter: 0,
						saleAreaName: "SUN SHINE",
						description: "140,00 TL harcama (kafeterya) yapıldı. Yeni bakiye: 0,00 TL",
						type: "SALE",
					},
				],
			});
			showToast("Kart yüklendi");
			return Promise.resolve(true);
		}
		return fetch("/api/cards/" + encodeURIComponent(key) + "/detail", { headers: authHeaders() })
			.then(function (r) {
				if (r.status === 401) {
					window.location.replace("/index.html");
					return null;
				}
				if (!r.ok) {
					throw new Error("nf");
				}
				return r.json();
			})
			.then(function (d) {
				if (!d) {
					return false;
				}
				applyUrunCardDetail(d);
				showToast("Kart yüklendi");
				return true;
			})
			.catch(function () {
				showToast("Kart yüklenemedi");
				return false;
			});
	}

	function openUrunWithCard(uid) {
		var v = uid != null ? String(uid).trim() : "";
		if (!v) {
			showToast("Kart UID girin");
			return;
		}
		urunCardUid = v;
		var inp = document.getElementById("urun-card-input");
		if (inp) {
			inp.readOnly = false;
			inp.value = v;
		}
		setModule("urun");
	}

	document.getElementById("btn-urun-load-card").addEventListener("click", function () {
		openUrunCardLoadModal();
	});

	var urunCardInputEl = document.getElementById("urun-card-input");
	if (urunCardInputEl) {
		urunCardInputEl.addEventListener("beforeinput", function (e) {
			if (currentModule !== "urun" || e.target !== urunCardInputEl) {
				return;
			}
			if (urunCardInputEl.readOnly) {
				return;
			}
			var v = cleanUid(urunCardInputEl.value);
			var start = urunCardInputEl.selectionStart != null ? urunCardInputEl.selectionStart : 0;
			var end = urunCardInputEl.selectionEnd != null ? urunCardInputEl.selectionEnd : 0;
			if (end > start) {
				return;
			}
			if (
				e.inputType === "insertFromPaste" ||
				e.inputType === "insertText" ||
				e.inputType === "insertCompositionText"
			) {
				if (v.length >= URUN_UID_SCAN_BLOCK_LEN && start === v.length) {
					e.preventDefault();
					showToast("Önce Temizle ile UID alanını sıfırlayın");
				}
			}
		});
		urunCardInputEl.addEventListener("keydown", function (e) {
			if (e.key === "Enter") {
				e.preventDefault();
				document.getElementById("btn-urun-load-card").click();
				return;
			}
			if (urunCardInputEl.readOnly) {
				return;
			}
			var v = cleanUid(urunCardInputEl.value);
			var start = urunCardInputEl.selectionStart != null ? urunCardInputEl.selectionStart : 0;
			var end = urunCardInputEl.selectionEnd != null ? urunCardInputEl.selectionEnd : 0;
			if (end > start) {
				return;
			}
			if (e.key.length === 1 && /[0-9a-fA-F]/.test(e.key)) {
				if (v.length >= URUN_UID_SCAN_BLOCK_LEN && start === v.length) {
					e.preventDefault();
					showToast("Önce Temizle ile UID alanını sıfırlayın");
				}
			}
		});
	}

	var btnUrunClearUid = document.getElementById("btn-urun-clear-uid");
	if (btnUrunClearUid) {
		btnUrunClearUid.addEventListener("click", function () {
			if (currentModule === "urun") {
				clearUrunCardPanel(true);
			}
		});
	}

	function updateSorguDisplay() {
		if (!sorguDigits) {
			sorguDisplay.textContent = "000 000 000 000";
			sorguDisplay.classList.add("is-placeholder");
		} else {
			sorguDisplay.classList.remove("is-placeholder");
			var chunks = [];
			for (var i = 0; i < sorguDigits.length; i += 4) {
				chunks.push(sorguDigits.slice(i, i + 4));
			}
			sorguDisplay.textContent = chunks.join(" ");
		}
	}

	function sorguClear() {
		sorguDigits = "";
		updateSorguDisplay();
	}

	function statusTr(status) {
		if (status === "ACTIVE") {
			return "Aktif";
		}
		if (status === "BLOCKED") {
			return "Bloke";
		}
		return status || "—";
	}

	function buildSorguKeypad() {
		var rows = [
			["7", "8", "9"],
			["4", "5", "6"],
			["1", "2", "3"],
			["0"],
		];
		sorguKeypadEl.innerHTML = "";
		rows.forEach(function (row) {
			row.forEach(function (key) {
				var b = document.createElement("button");
				b.type = "button";
				b.className = "sorgu-key" + (key === "0" ? " sorgu-key-zero" : "");
				b.textContent = key;
				b.addEventListener("click", function () {
					if (sorguDigits.length < MAX_SORGU_DIGITS) {
						sorguDigits += key;
						updateSorguDisplay();
					}
				});
				sorguKeypadEl.appendChild(b);
			});
		});
	}

	function subtotal() {
		return cart.reduce(function (s, i) {
			return s + i.price;
		}, 0);
	}

	function updateSummary() {
		const sub = subtotal();
		const discount = sub * effectiveDiscount();
		const due = Math.max(0, sub - discount);
		elDiscount.textContent = money(discount);
		elDue.textContent = money(due);

		let cash = 0;
		let card = 0;
		let credit = 0;
		if (payMode === "cash") cash = due;
		else if (payMode === "card") card = due;
		else if (payMode === "credit") credit = due;

		elCash.textContent = money(cash);
		elCard.textContent = money(card);
		elCredit.textContent = money(credit);
		elChange.textContent = money(0);
		updateContextBar();
	}

	function renderTicketGrid() {
		if (!gridEl.dataset.ticketsBuilt) {
			gridEl.innerHTML = "";
			TICKETS.forEach(function (t) {
				var btn = document.createElement("button");
				btn.type = "button";
				btn.className = "tile" + (selectedTileId === t.id ? " selected" : "");
				btn.dataset.id = t.id;
				btn.innerHTML =
					'<span class="tile-title">' +
					escapeHtml(t.label) +
					'</span><span class="tile-price">' +
					money(t.price) +
					'</span><span class="tile-meta">Sepete eklemek için dokunun</span>';
				btn.addEventListener("click", function () {
					selectedTileId = t.id;
					cart.push({ id: t.id, label: t.label, price: t.price });
					renderTicketGrid();
					updateSummary();
				});
				gridEl.appendChild(btn);
			});
			gridEl.dataset.ticketsBuilt = "1";
		} else {
			gridEl.querySelectorAll(".tile").forEach(function (btn) {
				var id = btn.dataset.id;
				btn.classList.toggle("selected", selectedTileId === id);
			});
		}
	}

	function renderGrid() {
		if (kartMode === "products") {
			delete gridEl.dataset.ticketsBuilt;
			gridEl.innerHTML = "";
			if (!kartProducts.length) {
				var empty = document.createElement("p");
				empty.className = "kart-grid-empty";
				empty.textContent = "Bu alanda tanımlı ürün yok.";
				gridEl.appendChild(empty);
				return;
			}
			kartProducts.forEach(function (p) {
				var id = "p" + p.id;
				var price = Number(p.price);
				if (isNaN(price)) price = 0;
				var btn = document.createElement("button");
				btn.type = "button";
				btn.className = "tile";
				btn.dataset.id = id;
				btn.innerHTML =
					'<span class="tile-title">' +
					escapeHtml(p.name) +
					'</span><span class="tile-price">' +
					money(price) +
					'</span><span class="tile-meta">Sepete eklemek için dokunun</span>';
				btn.addEventListener("click", function () {
					cart.push({
						id: id,
						productId: p.id,
						label: p.name,
						price: price,
					});
					// Tüm ızgarayı yeniden çizme — çok ürün varsa gecikme yaratır; özet anında güncellenir.
					updateSummary();
				});
				gridEl.appendChild(btn);
			});
			return;
		}
		renderTicketGrid();
	}

	function updateBakiyeDisplay() {
		bakiyeDisplay.textContent = money(keypadValue / 100);
	}

	function updateBakiyeSummary() {
		const total = keypadValue / 100;
		bakSumTotal.textContent = money(total);
		let cash = 0;
		let card = 0;
		if (bakiyePayMode === "cash" || bakiyePayMode === "rate") {
			cash = total;
		} else if (bakiyePayMode === "card") {
			card = total;
		}
		bakSumCash.textContent = money(cash);
		bakSumCard.textContent = money(card);
		bakSumChange.textContent = money(0);
	}

	function appendDigit(d) {
		keypadValue = Math.min(MAX_KURUS, keypadValue * 10 + d);
		updateBakiyeDisplay();
		updateBakiyeSummary();
	}

	function appendDoubleZero() {
		keypadValue = Math.min(MAX_KURUS, keypadValue * 100);
		updateBakiyeDisplay();
		updateBakiyeSummary();
	}

	function bakiyeBackspace() {
		keypadValue = Math.floor(keypadValue / 10);
		updateBakiyeDisplay();
		updateBakiyeSummary();
	}

	function bakiyeClear() {
		keypadValue = 0;
		updateBakiyeDisplay();
		updateBakiyeSummary();
	}

	function buildBakiyeKeypad() {
		const rows = [
			["1", "2", "3"],
			["4", "5", "6"],
			["7", "8", "9"],
			["00", "0", "clear"],
		];
		bakiyeKeypadEl.innerHTML = "";
		rows.forEach(function (row) {
			row.forEach(function (key) {
				const b = document.createElement("button");
				b.type = "button";
				b.className = "bakiye-key" + (key === "clear" ? " bakiye-key-wide" : "");
				if (key === "clear") {
					b.textContent = "Temizle";
					b.addEventListener("click", function () {
						bakiyeClear();
					});
				} else if (key === "00") {
					b.textContent = "00";
					b.addEventListener("click", appendDoubleZero);
				} else {
					b.textContent = key;
					const d = parseInt(key, 10);
					b.addEventListener("click", function () {
						appendDigit(d);
					});
				}
				bakiyeKeypadEl.appendChild(b);
			});
		});
	}

	document.querySelectorAll(".nav-item[data-module]").forEach(function (btn) {
		btn.addEventListener("click", function () {
			var m = btn.getAttribute("data-module");
			if (m === "kart") {
				setModule("kart");
			} else if (m === "bakiye") {
				setModule("bakiye");
			} else if (m === "urun") {
				setModule("urun");
			} else if (m === "sorgu") {
				setModule("sorgu");
			}
		});
	});

	var btnUrunGoBakiye = document.getElementById("btn-urun-go-bakiye");
	if (btnUrunGoBakiye) {
		btnUrunGoBakiye.addEventListener("click", function () {
			setModule("bakiye");
		});
	}

	document.getElementById("nav-exit").addEventListener("click", function () {
		sessionStorage.removeItem(TOKEN_KEY);
		sessionStorage.removeItem(USER_KEY);
		sessionStorage.removeItem(ROLE_KEY);
		sessionStorage.removeItem("aqua_display_name");
		sessionStorage.removeItem("aqua_sale_areas");
		sessionStorage.removeItem("aqua_ticket_sales");
		sessionStorage.removeItem("aqua_balance_load");
		window.location.href = "/index.html";
	});

	document.querySelectorAll(".pay-option").forEach(function (opt) {
		opt.addEventListener("click", function () {
			document.querySelectorAll(".pay-option").forEach(function (o) {
				o.classList.remove("active");
			});
			opt.classList.add("active");
			payMode = opt.getAttribute("data-pay") || "cash";
			updateSummary();
		});
	});

	document.querySelectorAll(".bakiye-pay-opt").forEach(function (opt) {
		opt.addEventListener("click", function () {
			document.querySelectorAll(".bakiye-pay-opt").forEach(function (o) {
				o.classList.remove("active");
			});
			opt.classList.add("active");
			bakiyePayMode = opt.getAttribute("data-bakiye-pay") || "cash";
			updateBakiyeSummary();
			if (bakiyePayMode === "rate") {
				showToast("Nakit kur — demo (nakit ile aynı özet)");
			}
		});
	});

	document.querySelectorAll("#bakiye-quick .bakiye-quick-btn").forEach(function (btn) {
		btn.addEventListener("click", function () {
			const add = parseInt(btn.getAttribute("data-add-kurus"), 10);
			if (!isNaN(add)) {
				keypadValue = Math.min(MAX_KURUS, keypadValue + add);
				updateBakiyeDisplay();
				updateBakiyeSummary();
			}
		});
	});

	document.getElementById("bakiye-backspace").addEventListener("click", bakiyeBackspace);

	document.getElementById("sorgu-backspace").addEventListener("click", function () {
		sorguDigits = sorguDigits.slice(0, -1);
		updateSorguDisplay();
	});

	document.getElementById("btn-sorgula").addEventListener("click", function () {
		openSorguInquiryModal();
	});

	document.getElementById("btn-clear").addEventListener("click", function () {
		if (currentModule === "kart" && kartMode === "products") {
			cart = [];
			selectedTileId = null;
			discountPercent = 0;
			renderGrid();
			updateSummary();
			showToast("Sepet temizlendi");
			return;
		}
		if (currentModule === "urun") {
			clearUrunCardPanel(true);
			return;
		}
		if (currentModule === "sorgu") {
			sorguClear();
			showToast("Giriş temizlendi");
			return;
		}
		if (currentModule === "bakiye") {
			bakiyeClear();
			showToast("Tutar sıfırlandı");
			return;
		}
		cart = [];
		selectedTileId = null;
		discountPercent = 0;
		renderGrid();
		updateSummary();
		showToast("Sepet temizlendi");
	});

	document.getElementById("btn-code").addEventListener("click", function () {
		if (currentModule !== "kart" || kartMode !== "tickets") return;
		var p = prompt("İndirim kodu (demo: INDIR10 = %10)");
		if (p && p.trim().toUpperCase() === "INDIR10") {
			discountPercent = 0.1;
			updateSummary();
			showToast("%10 indirim uygulandı");
		} else if (p) {
			showToast("Geçersiz kod");
		}
	});

	document.getElementById("btn-complete").addEventListener("click", function () {
		if (currentModule === "sorgu") {
			openSorguInquiryModal();
			return;
		}
		if (currentModule === "bakiye") {
			openBakiyeCardBindModal();
			return;
		}
		if (kartMode === "products") {
			if (cart.length === 0) {
				showToast("Önce ürün seçin");
				return;
			}
			openProductSaleCardModal();
			return;
		}
		if (cart.length === 0) {
			showToast("Önce bilet seçin");
			return;
		}
		openTicketCardBindModal();
	});

	var gridViewport = document.querySelector("#view-kart .pos-grid-viewport");
	var scrollLeftBtn = document.getElementById("scroll-left");
	var scrollRightBtn = document.getElementById("scroll-right");

	/** Bir “sayfa”: görünür ızgara genişliği kadar kaydır (yatay) */
	function kartGridScrollPage() {
		if (!gridViewport) {
			return Math.round(window.innerWidth * 0.4);
		}
		return Math.max(80, gridViewport.clientWidth - 2);
	}

	function updateKartScrollButtons() {
		if (!gridViewport || !scrollLeftBtn || !scrollRightBtn) {
			return;
		}
		var maxScroll = gridViewport.scrollWidth - gridViewport.clientWidth;
		if (maxScroll <= 1) {
			scrollLeftBtn.disabled = true;
			scrollRightBtn.disabled = true;
			return;
		}
		scrollLeftBtn.disabled = gridViewport.scrollLeft <= 1;
		scrollRightBtn.disabled = gridViewport.scrollLeft >= maxScroll - 1;
	}

	if (gridViewport && scrollLeftBtn && scrollRightBtn) {
		scrollLeftBtn.addEventListener("click", function () {
			gridViewport.scrollBy({ left: -kartGridScrollPage(), behavior: "smooth" });
		});
		scrollRightBtn.addEventListener("click", function () {
			gridViewport.scrollBy({ left: kartGridScrollPage(), behavior: "smooth" });
		});
		gridViewport.addEventListener("scroll", updateKartScrollButtons, { passive: true });
		window.addEventListener("resize", updateKartScrollButtons, { passive: true });
		var posGridEl = document.getElementById("pos-grid");
		if (posGridEl) {
			var mo = new MutationObserver(function () {
				requestAnimationFrame(updateKartScrollButtons);
			});
			mo.observe(posGridEl, { childList: true, subtree: true });
		}
		requestAnimationFrame(updateKartScrollButtons);
	}

	var POS_UI_SCALE_MIN = 0.4;
	var POS_UI_SCALE_MAX = 1.5;
	var POS_UI_SCALE_STEP = 0.05;
	var POS_UI_SCALE_DEFAULT = 1.24; /* %124 — ilk açılış; kullanıcı slider ile değiştirir */
	var POS_UI_SCALE_KEY = "aqua_pos_ui_scale";

	function initPosZoom() {
		var wrap = document.getElementById("pos-zoom-wrap");
		var range = document.getElementById("pos-zoom-range");
		var pctEl = document.getElementById("pos-zoom-pct");
		var btnOut = document.getElementById("pos-zoom-out");
		var btnIn = document.getElementById("pos-zoom-in");
		if (!wrap || !range || !pctEl || !btnOut || !btnIn) {
			return;
		}
		if (document.documentElement.classList.contains("pos-perf")) {
			wrap.hidden = true;
			var shellPerf = document.getElementById("pos-main-scale");
			if (shellPerf) {
				shellPerf.style.removeProperty("zoom");
			}
			return;
		}

		function clampScale(x) {
			if (x < POS_UI_SCALE_MIN) {
				return POS_UI_SCALE_MIN;
			}
			if (x > POS_UI_SCALE_MAX) {
				return POS_UI_SCALE_MAX;
			}
			return x;
		}

		function readScale() {
			try {
				var stored = parseFloat(localStorage.getItem(POS_UI_SCALE_KEY) || "");
				if (!isNaN(stored) && stored >= POS_UI_SCALE_MIN && stored <= POS_UI_SCALE_MAX) {
					return clampScale(stored);
				}
			} catch (e) {}
			var st = getComputedStyle(document.documentElement).getPropertyValue("--pos-terminal-ui-scale").trim();
			var n = parseFloat(st);
			if (!isNaN(n) && n >= POS_UI_SCALE_MIN && n <= POS_UI_SCALE_MAX) {
				return clampScale(n);
			}
			return POS_UI_SCALE_DEFAULT;
		}

		function applyScale(scale) {
			scale = clampScale(scale);
			document.documentElement.style.setProperty("--pos-terminal-ui-scale", String(scale));
			var shell = document.getElementById("pos-main-scale");
			if (shell) {
				shell.style.zoom = String(scale);
			}
			try {
				localStorage.setItem(POS_UI_SCALE_KEY, scale.toFixed(6));
			} catch (e) {}
			var p = Math.round(scale * 100);
			range.value = String(p);
			range.setAttribute("aria-valuenow", String(p));
			pctEl.textContent = p + "%";
		}

		applyScale(readScale());

		range.addEventListener("input", function () {
			var v = parseInt(range.value, 10);
			if (isNaN(v)) {
				return;
			}
			applyScale(v / 100);
		});

		btnOut.addEventListener("click", function () {
			applyScale(readScale() - POS_UI_SCALE_STEP);
		});
		btnIn.addEventListener("click", function () {
			applyScale(readScale() + POS_UI_SCALE_STEP);
		});
	}

	initPosZoom();

	function tick() {
		var now = new Date();
		clockEl.textContent = now.toLocaleTimeString("tr-TR", { hour: "2-digit", minute: "2-digit", second: "2-digit" });
		var fd = document.getElementById("footer-date");
		if (fd) {
			fd.textContent = now.toLocaleDateString("tr-TR", { day: "numeric", month: "short", year: "numeric", weekday: "short" });
		}
	}
	tick();
	setInterval(tick, 1000);

	function bootstrapPos() {
		buildBakiyeKeypad();
		buildSorguKeypad();
		updateSummary();
		setModule(pickInitialModule());
	}

	fetch("/api/sale-areas", { headers: authHeaders() })
		.then(function (r) {
			if (r.status === 401) {
				window.location.replace("/index.html");
				return null;
			}
			if (!r.ok) {
				throw new Error("http");
			}
			return r.json();
		})
		.then(function (areas) {
			if (!areas) areas = [];
			areas.forEach(function (a) {
				saleAreaNamesByCode[a.code] = a.name || a.code;
			});
			initKartMode();
			updateKartNavLabel();
			syncKartViewUi();
			if (kartMode === "products" && kartProductAreaCode) {
				loadKartProductsForArea(kartProductAreaCode, function () {
					renderGrid();
					bootstrapPos();
				});
			} else {
				renderGrid();
				bootstrapPos();
			}
		})
		.catch(function () {
			initKartMode();
			renderGrid();
			bootstrapPos();
		});
})();
