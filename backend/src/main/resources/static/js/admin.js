(function () {
	const TOKEN_KEY = "aqua_token";
	const ROLE_KEY = "aqua_role";

	if (window.location.protocol === "file:") {
		window.alert(
			"Bu sayfayı dosya olarak (file://) açmayın.\n\nTarayıcı adres çubuğuna yazın:\nhttp://127.0.0.1:8081/admin.html\n\n(Aksi halde /api/... istekleri 404 Not Found verir.)"
		);
	}

	if (!sessionStorage.getItem(TOKEN_KEY)) {
		window.location.replace("/index.html");
		return;
	}
	if (sessionStorage.getItem(ROLE_KEY) !== "ADMIN") {
		window.location.replace("/pos.html");
		return;
	}

	const alertEl = document.getElementById("admin-alert");
	const tbody = document.getElementById("users-tbody");
	const editPanel = document.getElementById("edit-panel");
	const formEdit = document.getElementById("form-edit");
	const formCreate = document.getElementById("form-create");

	function authHeaders() {
		return {
			Authorization: "Bearer " + sessionStorage.getItem(TOKEN_KEY),
			Accept: "application/json",
		};
	}

	function authHeadersJson() {
		return Object.assign({}, authHeaders(), { "Content-Type": "application/json" });
	}

	function showAlert(msg, type) {
		alertEl.textContent = msg;
		alertEl.hidden = false;
		alertEl.className = "admin-alert " + (type === "ok" ? "ok" : "error");
	}

	function hideAlert() {
		alertEl.hidden = true;
	}

	const roleLabels = { ADMIN: "Yönetici", SUPERVISOR: "Süpervizör", CASHIER: "Kasiyer" };

	const saleAreaShort = { BEVERAGE: "İçecek", BAKERY: "Fırın", ALCOHOL: "Alkol", ICE_CREAM: "Dondurma" };

	function formatSaleAreas(codes) {
		if (!codes || !codes.length) {
			return "—";
		}
		return codes
			.map(function (c) {
				return saleAreaShort[c] || c;
			})
			.join(", ");
	}

	function collectSaleAreas(formEl) {
		var out = [];
		formEl.querySelectorAll('input[name="sale-area"]:checked').forEach(function (cb) {
			out.push(cb.value);
		});
		return out;
	}

	function renderRows(users) {
		tbody.innerHTML = "";
		users.forEach(function (u) {
			var tr = document.createElement("tr");
			tr.innerHTML =
				"<td>" +
				escapeHtml(u.userId) +
				"</td>" +
				"<td>" +
				escapeHtml(u.displayName || "—") +
				"</td>" +
				"<td>" +
				escapeHtml(roleLabels[u.role] || u.role) +
				"</td>" +
				"<td>" +
				(u.ticketSalesAllowed !== false
					? '<span class="badge ok">Evet</span>'
					: '<span class="badge off">Hayır</span>') +
				"</td>" +
				"<td>" +
				(u.balanceLoadAllowed !== false
					? '<span class="badge ok">Evet</span>'
					: '<span class="badge off">Hayır</span>') +
				"</td>" +
				'<td class="td-areas">' +
				escapeHtml(formatSaleAreas(u.saleAreaCodes)) +
				"</td>" +
				"<td>" +
				(u.active ? '<span class="badge ok">Aktif</span>' : '<span class="badge off">Pasif</span>') +
				"</td>" +
				'<td class="actions"><button type="button" class="btn btn-ghost btn-sm" data-edit="' +
				u.id +
				'">Düzenle</button> <button type="button" class="btn btn-danger btn-sm" data-del="' +
				u.id +
				'" data-sicil="' +
				escapeAttr(u.userId) +
				'">Sil</button></td>';
			tbody.appendChild(tr);
		});

		tbody.querySelectorAll("[data-edit]").forEach(function (btn) {
			btn.addEventListener("click", function () {
				var id = Number(btn.getAttribute("data-edit"));
				var u = users.find(function (x) {
					return x.id === id;
				});
				if (u) openEdit(u);
			});
		});

		tbody.querySelectorAll("[data-del]").forEach(function (btn) {
			btn.addEventListener("click", function () {
				var id = Number(btn.getAttribute("data-del"));
				var sicil = btn.getAttribute("data-sicil");
				if (!confirm("Sicil " + sicil + " silinsin mi?")) return;
				delUser(id);
			});
		});
	}

	function escapeHtml(s) {
		if (s == null) return "";
		var d = document.createElement("div");
		d.textContent = String(s);
		return d.innerHTML;
	}

	function escapeAttr(s) {
		return String(s).replace(/"/g, "&quot;");
	}

	function loadUsers() {
		// eslint-disable-next-line no-undef
		fetch("/api/admin/users", { headers: authHeaders() })
			.then(function (r) {
				if (r.status === 401) {
					window.location.replace("/index.html");
					return null;
				}
				if (r.status === 403) {
					showAlert("Bu sayfa için yönetici yetkisi gerekir.", "err");
					return null;
				}
				if (!r.ok) throw new Error("http");
				return r.json();
			})
			.then(function (data) {
				if (!data) return;
				renderRows(data);
			})
			.catch(function () {
				showAlert("Liste yüklenemedi.", "err");
			});
	}

	function openEdit(u) {
		document.getElementById("e-id").value = u.id;
		document.getElementById("e-userId").value = u.userId;
		document.getElementById("e-password").value = "";
		document.getElementById("e-displayName").value = u.displayName || "";
		document.getElementById("e-role").value = u.role;
		document.getElementById("e-active").checked = u.active;
		formEdit.querySelectorAll('input[name="sale-area"]').forEach(function (cb) {
			cb.checked = Array.isArray(u.saleAreaCodes) && u.saleAreaCodes.indexOf(cb.value) >= 0;
		});
		document.getElementById("e-ticket").checked = u.ticketSalesAllowed !== false;
		document.getElementById("e-balance").checked = u.balanceLoadAllowed !== false;
		editPanel.hidden = false;
		editPanel.scrollIntoView({ behavior: "smooth", block: "nearest" });
	}

	document.getElementById("btn-edit-cancel").addEventListener("click", function () {
		editPanel.hidden = true;
	});

	formCreate.addEventListener("submit", function (e) {
		e.preventDefault();
		hideAlert();
		var body = {
			userId: document.getElementById("c-userId").value.trim(),
			password: document.getElementById("c-password").value,
			displayName: document.getElementById("c-displayName").value.trim() || null,
			role: document.getElementById("c-role").value,
			saleAreaCodes: collectSaleAreas(formCreate),
			ticketSalesAllowed: document.getElementById("c-ticket").checked,
			balanceLoadAllowed: document.getElementById("c-balance").checked,
		};
		fetch("/api/admin/users", {
			method: "POST",
			headers: authHeadersJson(),
			body: JSON.stringify(body),
		})
			.then(async function (r) {
				var data = await r.json().catch(function () {
					return {};
				});
				if (r.status === 401) {
					window.location.replace("/index.html");
					return;
				}
				if (!r.ok) {
					showAlert(data.detail || data.title || "Kayıt başarısız", "err");
					return;
				}
				showAlert("Kullanıcı oluşturuldu.", "ok");
				formCreate.reset();
				loadUsers();
			})
			.catch(function () {
				showAlert("İstek başarısız.", "err");
			});
	});

	formEdit.addEventListener("submit", function (e) {
		e.preventDefault();
		hideAlert();
		var id = document.getElementById("e-id").value;
		var pw = document.getElementById("e-password").value;
		var body = {
			displayName: document.getElementById("e-displayName").value.trim() || null,
			role: document.getElementById("e-role").value,
			active: document.getElementById("e-active").checked,
			saleAreaCodes: collectSaleAreas(formEdit),
			ticketSalesAllowed: document.getElementById("e-ticket").checked,
			balanceLoadAllowed: document.getElementById("e-balance").checked,
		};
		if (pw) body.password = pw;

		fetch("/api/admin/users/" + encodeURIComponent(id), {
			method: "PUT",
			headers: authHeadersJson(),
			body: JSON.stringify(body),
		})
			.then(async function (r) {
				var data = await r.json().catch(function () {
					return {};
				});
				if (r.status === 401) {
					window.location.replace("/index.html");
					return;
				}
				if (!r.ok) {
					showAlert(data.detail || "Güncelleme başarısız", "err");
					return;
				}
				showAlert("Kaydedildi.", "ok");
				editPanel.hidden = true;
				loadUsers();
			})
			.catch(function () {
				showAlert("İstek başarısız.", "err");
			});
	});

	function delUser(id) {
		fetch("/api/admin/users/" + encodeURIComponent(id), {
			method: "DELETE",
			headers: authHeaders(),
		})
			.then(async function (r) {
				if (r.status === 401) {
					window.location.replace("/index.html");
					return;
				}
				if (!r.ok) {
					var d = await r.json().catch(function () {
						return {};
					});
					showAlert(d.detail || "Silinemedi", "err");
					return;
				}
				showAlert("Kullanıcı silindi.", "ok");
				loadUsers();
			})
			.catch(function () {
				showAlert("İstek başarısız.", "err");
			});
	}

	var fmtTry = new Intl.NumberFormat("tr-TR", {
		style: "currency",
		currency: "TRY",
		minimumFractionDigits: 2,
	});

	function moneyFmt(n) {
		return fmtTry.format(Number(n) || 0);
	}

	function loadAdminProductCatalog() {
		var root = document.getElementById("admin-catalog-root");
		var emptyEl = document.getElementById("admin-catalog-empty");
		if (!root || !emptyEl) {
			return;
		}
		Promise.all([
			fetch("/api/sale-areas", { headers: authHeaders() }).then(function (r) {
				if (r.status === 401) {
					window.location.replace("/index.html");
					return null;
				}
				if (!r.ok) {
					throw new Error("http");
				}
				return r.json();
			}),
			fetch("/api/admin/products", { headers: authHeaders() }).then(function (r) {
				if (r.status === 401) {
					window.location.replace("/index.html");
					return null;
				}
				if (!r.ok) {
					throw new Error("http");
				}
				return r.json();
			}),
		])
			.then(function (pair) {
				var areas = pair[0];
				var products = pair[1];
				if (!areas || !products) {
					return;
				}
				if (!areas.length) {
					root.innerHTML = "";
					emptyEl.hidden = false;
					emptyEl.textContent = "Satış alanı tanımlı değil.";
					return;
				}
				emptyEl.hidden = true;
				var byCode = {};
				products.forEach(function (p) {
					var c = p.saleAreaCode;
					if (!byCode[c]) {
						byCode[c] = [];
					}
					byCode[c].push(p);
				});
				root.innerHTML = "";
				areas.forEach(function (area) {
					var list = byCode[area.code] || [];
					var block = document.createElement("section");
					block.className = "admin-catalog-area";

					var head = document.createElement("div");
					head.className = "admin-catalog-area-head";
					var h3 = document.createElement("h3");
					h3.textContent = area.name || area.code;
					head.appendChild(h3);
					var addInline = document.createElement("button");
					addInline.type = "button";
					addInline.className = "btn btn-ghost btn-sm";
					addInline.textContent = "+ Bu alana ekle";
					addInline.addEventListener("click", function () {
						openAdminProductModal(null, area.code);
					});
					head.appendChild(addInline);
					block.appendChild(head);

					var wrap = document.createElement("div");
					wrap.className = "admin-grid-scroll-wrap";
					var scrollL = document.createElement("button");
					scrollL.type = "button";
					scrollL.className = "admin-grid-scroll";
					scrollL.setAttribute("aria-label", "Sola kaydır");
					scrollL.innerHTML =
						'<svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke-width="2" stroke="currentColor"><path stroke-linecap="round" stroke-linejoin="round" d="M15.75 19.5 8.25 12l7.5-7.5" /></svg>';
					var viewport = document.createElement("div");
					viewport.className = "admin-grid-viewport";
					var grid = document.createElement("div");
					grid.className = "admin-prod-grid";
					if (list.length === 0) {
						grid.classList.add("admin-prod-grid--empty");
						var ph = document.createElement("div");
						ph.className = "admin-prod-empty-placeholder";
						ph.setAttribute("role", "status");
						ph.textContent = "Bu alanda henüz ürün yok.";
						grid.appendChild(ph);
					}
					list.forEach(function (p) {
						var btn = document.createElement("button");
						btn.type = "button";
						var tc = "admin-prod-tile";
						if (p.active === false) {
							tc += " tile--inactive";
						}
						btn.className = tc;
						var badge = "";
						if (p.active === false) {
							badge = '<span class="tile-badge-pasif">Pasif</span>';
						}
						btn.innerHTML =
							badge +
							'<span class="tile-title">' +
							escapeHtml(p.name) +
							'</span><span class="tile-price">' +
							moneyFmt(p.price) +
							'</span><span class="tile-meta">Düzenlemek için dokunun</span>';
						btn.addEventListener("click", function () {
							openAdminProductModal(p, null);
						});
						grid.appendChild(btn);
					});
					function syncAdminCatalogScroll() {
						var need = viewport.scrollWidth > viewport.clientWidth + 2;
						scrollL.disabled = !need;
						scrollR.disabled = !need;
						scrollL.classList.toggle("admin-grid-scroll--disabled", !need);
						scrollR.classList.toggle("admin-grid-scroll--disabled", !need);
					}
					viewport.appendChild(grid);
					var scrollR = document.createElement("button");
					scrollR.type = "button";
					scrollR.className = "admin-grid-scroll";
					scrollR.setAttribute("aria-label", "Sağa kaydır");
					scrollR.innerHTML =
						'<svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke-width="2" stroke="currentColor"><path stroke-linecap="round" stroke-linejoin="round" d="m8.25 4.5 7.5 7.5-7.5 7.5" /></svg>';
					wrap.appendChild(scrollL);
					wrap.appendChild(viewport);
					wrap.appendChild(scrollR);
					block.appendChild(wrap);

					scrollL.addEventListener("click", function () {
						viewport.scrollBy({ left: -viewport.clientWidth * 0.85, behavior: "smooth" });
					});
					scrollR.addEventListener("click", function () {
						viewport.scrollBy({ left: viewport.clientWidth * 0.85, behavior: "smooth" });
					});

					requestAnimationFrame(function () {
						requestAnimationFrame(syncAdminCatalogScroll);
					});

					root.appendChild(block);
				});
			})
			.catch(function () {
				showAlert("Ürün listesi yüklenemedi.", "err");
			});
	}

	function fetchSaleAreasForAdminModal() {
		return fetch("/api/sale-areas", { headers: authHeaders() }).then(function (r) {
			if (!r.ok) {
				throw new Error("http");
			}
			return r.json();
		});
	}

	function openAdminProductModal(product, presetAreaCode) {
		var modal = document.getElementById("admin-modal-product");
		var title = document.getElementById("admin-modal-product-title");
		var idEl = document.getElementById("admin-prod-id");
		var sel = document.getElementById("admin-prod-sale-area");
		var nameEl = document.getElementById("admin-prod-name");
		var priceEl = document.getElementById("admin-prod-price");
		var stockEl = document.getElementById("admin-prod-stock");
		var activeWrap = document.getElementById("admin-prod-active-wrap");
		var activeCb = document.getElementById("admin-prod-active");
		var delBtn = document.getElementById("admin-btn-modal-delete");
		if (!modal || !sel || !nameEl || !priceEl) {
			return;
		}
		fetchSaleAreasForAdminModal()
			.then(function (areas) {
				sel.innerHTML = "";
				areas.forEach(function (a) {
					var o = document.createElement("option");
					o.value = a.code;
					o.textContent = a.name || a.code;
					sel.appendChild(o);
				});
				if (product) {
					title.textContent = "Ürünü düzenle";
					idEl.value = String(product.id);
					sel.value = product.saleAreaCode;
					nameEl.value = product.name || "";
					priceEl.value = String(product.price);
					stockEl.value = product.stockQuantity != null ? String(product.stockQuantity) : "";
					activeWrap.hidden = false;
					activeCb.checked = product.active !== false;
					delBtn.hidden = false;
				} else {
					title.textContent = "Yeni ürün";
					idEl.value = "";
					sel.value = presetAreaCode || (areas[0] && areas[0].code) || "";
					nameEl.value = "";
					priceEl.value = "";
					stockEl.value = "";
					activeWrap.hidden = true;
					delBtn.hidden = true;
				}
				modal.hidden = false;
			})
			.catch(function () {
				showAlert("Satış alanları yüklenemedi.", "err");
			});
	}

	function closeAdminProductModal() {
		var modal = document.getElementById("admin-modal-product");
		if (modal) {
			modal.hidden = true;
		}
	}

	document.querySelectorAll(".admin-tab").forEach(function (tab) {
		tab.addEventListener("click", function () {
			var id = tab.getAttribute("data-panel");
			document.querySelectorAll(".admin-tab").forEach(function (t) {
				var on = t === tab;
				t.classList.toggle("active", on);
				t.setAttribute("aria-selected", on ? "true" : "false");
			});
			document.getElementById("admin-panel-users").hidden = id !== "users";
			document.getElementById("admin-panel-products").hidden = id !== "products";
			var panelCards = document.getElementById("admin-panel-cards");
			if (panelCards) {
				panelCards.hidden = id !== "cards";
			}
			var panelPrinter = document.getElementById("admin-panel-printer");
			if (panelPrinter) {
				panelPrinter.hidden = id !== "printer";
			}
			if (id === "products") {
				loadAdminProductCatalog();
			}
			if (id === "printer") {
				loadPrinterPorts();
			}
			if (id === "cards") {
				var uidIn = document.getElementById("card-issue-uid");
				if (uidIn) {
					uidIn.focus();
				}
			}
		});
	});

	function loadPrinterPorts() {
		var sel = document.getElementById("printer-port");
		if (!sel) {
			return;
		}
		hideAlert();
		fetch("/api/printer/ports", { headers: authHeaders() })
			.then(function (r) {
				if (r.status === 401) {
					sessionStorage.removeItem(TOKEN_KEY);
					window.location.replace("/index.html");
					return Promise.reject(new Error("401"));
				}
				if (!r.ok) {
					return r
						.json()
						.catch(function () {
							return {};
						})
						.then(function (data) {
							throw new Error(
								(data && (data.error || data.message)) || "HTTP " + r.status
							);
						});
				}
				return r.json();
			})
			.then(function (ports) {
				if (!ports || !Array.isArray(ports)) {
					return;
				}
				var cur = sel.value;
				sel.innerHTML = "";
				var opt0 = document.createElement("option");
				opt0.value = "";
				opt0.textContent = "— Port seçin —";
				sel.appendChild(opt0);
				ports.forEach(function (p) {
					var o = document.createElement("option");
					o.value = p.name;
					o.textContent = p.name + (p.description ? " — " + p.description : "");
					sel.appendChild(o);
				});
				if (cur) {
					sel.value = cur;
				}
			})
			.catch(function (e) {
				if (e && e.message === "401") {
					return;
				}
				showAlert(
					(e && e.message) || "Port listesi alınamadı. Oturum açık mı? Sunucu güncel JAR ile mi çalışıyor?",
					"err"
				);
			});
	}

	var btnPrinterTest = document.getElementById("btn-printer-test");
	if (btnPrinterTest) {
		btnPrinterTest.addEventListener("click", function () {
			var manualEl = document.getElementById("printer-port-manual");
			var manual = manualEl ? manualEl.value.trim() : "";
			var fromList = document.getElementById("printer-port").value.trim();
			var port = manual || fromList;
			var baud = parseInt(document.getElementById("printer-baud").value, 10);
			if (!port) {
				showAlert("Listeden port seçin veya (Mac) /dev/cu.… adresini elle yazın.", "err");
				return;
			}
			hideAlert();
			var modeEl = document.getElementById("printer-mode");
			var mode = modeEl ? modeEl.value : "full";
			fetch("/api/printer/test", {
				method: "POST",
				headers: authHeadersJson(),
				body: JSON.stringify({ port: port, baudRate: baud, mode: mode }),
			})
				.then(function (r) {
					return r.json().then(function (data) {
						return { ok: r.ok, data: data };
					});
				})
				.then(function (res) {
					if (!res.ok) {
						showAlert((res.data && res.data.error) || "Yazdırılamadı.", "err");
						return;
					}
					try {
						sessionStorage.setItem("aqua_printer_port", port);
						sessionStorage.setItem("aqua_printer_baud", String(baud));
					} catch (e) {
						/* ignore */
					}
					showAlert(res.data.message || "Test fişi gönderildi.", "ok");
				})
				.catch(function () {
					showAlert("İstek başarısız.", "err");
				});
		});
	}
	var btnPrinterRefresh = document.getElementById("btn-printer-refresh");
	if (btnPrinterRefresh) {
		btnPrinterRefresh.addEventListener("click", function () {
			loadPrinterPorts();
		});
	}

	var btnAdminCatalogAdd = document.getElementById("btn-admin-catalog-add");
	if (btnAdminCatalogAdd) {
		btnAdminCatalogAdd.addEventListener("click", function () {
			openAdminProductModal(null, null);
		});
	}

	var adminFormProduct = document.getElementById("admin-form-product");
	if (adminFormProduct) {
		adminFormProduct.addEventListener("submit", function (e) {
			e.preventDefault();
			var pid = document.getElementById("admin-prod-id").value.trim();
			var saleAreaCode = document.getElementById("admin-prod-sale-area").value;
			var name = document.getElementById("admin-prod-name").value.trim();
			var price = parseFloat(String(document.getElementById("admin-prod-price").value).replace(",", "."));
			var stockRaw = document.getElementById("admin-prod-stock").value.trim();
			var stock = stockRaw === "" ? null : parseInt(stockRaw, 10);
			if (!name || isNaN(price) || price < 0) {
				showAlert("Geçerli ad ve fiyat girin.", "err");
				return;
			}
			if (stock !== null && (isNaN(stock) || stock < 0)) {
				showAlert("Stok geçersiz.", "err");
				return;
			}
			if (pid) {
				fetch("/api/admin/products/" + encodeURIComponent(pid), {
					method: "PUT",
					headers: authHeadersJson(),
					body: JSON.stringify({
						name: name,
						price: price,
						stockQuantity: stock,
						active: document.getElementById("admin-prod-active").checked,
						saleAreaCode: saleAreaCode,
					}),
				})
					.then(function (r) {
						return r.json().then(function (data) {
							return { ok: r.ok, data: data };
						});
					})
					.then(function (res) {
						if (!res.ok) {
							showAlert((res.data && res.data.detail) || "Kaydedilemedi", "err");
							return;
						}
						showAlert("Ürün güncellendi.", "ok");
						closeAdminProductModal();
						loadAdminProductCatalog();
					})
					.catch(function () {
						showAlert("İstek başarısız.", "err");
					});
			} else {
				fetch("/api/admin/products", {
					method: "POST",
					headers: authHeadersJson(),
					body: JSON.stringify({
						saleAreaCode: saleAreaCode,
						name: name,
						price: price,
						stockQuantity: stock,
					}),
				})
					.then(function (r) {
						return r.json().then(function (data) {
							return { ok: r.ok, data: data };
						});
					})
					.then(function (res) {
						if (!res.ok) {
							showAlert((res.data && res.data.detail) || "Oluşturulamadı", "err");
							return;
						}
						showAlert("Ürün eklendi.", "ok");
						closeAdminProductModal();
						loadAdminProductCatalog();
					})
					.catch(function () {
						showAlert("İstek başarısız.", "err");
					});
			}
		});
	}

	var adminBtnModalCancel = document.getElementById("admin-btn-modal-cancel");
	var adminModalBackdrop = document.getElementById("admin-modal-product-backdrop");
	if (adminBtnModalCancel) {
		adminBtnModalCancel.addEventListener("click", closeAdminProductModal);
	}
	if (adminModalBackdrop) {
		adminModalBackdrop.addEventListener("click", closeAdminProductModal);
	}

	var cardUidInputEl = document.getElementById("card-issue-uid");
	var cardUidStatusEl = document.getElementById("card-issue-uid-status");
	var cardUidPulseTimer = null;
	var cardUidLastLen = 0;

	function setCardUidStatus(text, variant) {
		if (!cardUidStatusEl) {
			return;
		}
		cardUidStatusEl.textContent = text;
		cardUidStatusEl.className =
			"field-hint card-uid-status" +
			(variant === "ok" ? " card-uid-status--ok" : " card-uid-status--wait");
	}

	function pulseCardUidInput() {
		if (!cardUidInputEl) {
			return;
		}
		cardUidInputEl.classList.add("card-issue-uid-input--tick");
		if (cardUidPulseTimer) {
			clearTimeout(cardUidPulseTimer);
		}
		cardUidPulseTimer = setTimeout(function () {
			cardUidInputEl.classList.remove("card-issue-uid-input--tick");
		}, 240);
	}

	function resetCardUidLiveHint() {
		cardUidLastLen = 0;
		setCardUidStatus(
			"Kutuya tıklayın; imleç yanıp sönüyorsa kartı okutun. Rakamlar burada birikirse okuyucu veri gönderiyor demektir.",
			"wait"
		);
	}

	if (cardUidInputEl && cardUidStatusEl) {
		resetCardUidLiveHint();
		cardUidInputEl.addEventListener("input", function () {
			var v = cardUidInputEl.value;
			var n = v.length;
			if (n > cardUidLastLen) {
				pulseCardUidInput();
			}
			cardUidLastLen = n;
			if (n === 0) {
				resetCardUidLiveHint();
				return;
			}
			setCardUidStatus(
				n + " karakter alındı — okuyucu çalışıyor gibi görünüyor. Bitince Enter da gelebilir; gerekirse sonunu silin.",
				"ok"
			);
		});
		cardUidInputEl.addEventListener("focus", function () {
			if (!cardUidInputEl.value) {
				setCardUidStatus("Hazır — kartı okutun veya elle yazın.", "wait");
			}
		});
	}

	var formIssueCard = document.getElementById("form-issue-card");
	if (formIssueCard) {
		formIssueCard.addEventListener("submit", function (e) {
			e.preventDefault();
			var uidEl = document.getElementById("card-issue-uid");
			var balEl = document.getElementById("card-issue-balance");
			var uid = uidEl ? uidEl.value.trim() : "";
			var balRaw = balEl ? String(balEl.value).trim().replace(",", ".") : "";
			var bal = parseFloat(balRaw);
			if (!uid) {
				showAlert("Kart UID girin.", "err");
				return;
			}
			if (isNaN(bal) || bal < 0) {
				showAlert("Geçerli bakiye girin.", "err");
				return;
			}
			hideAlert();
			fetch("/api/cards", {
				method: "POST",
				headers: authHeadersJson(),
				body: JSON.stringify({ uid: uid, initialBalance: bal }),
			})
				.then(async function (r) {
					var data = await r.json().catch(function () {
						return {};
					});
					if (r.status === 401) {
						window.location.replace("/index.html");
						return;
					}
					if (!r.ok) {
						showAlert(data.detail || data.title || "Kayıt başarısız", "err");
						return;
					}
					showAlert(
						"Kart tanımlandı: " + uid + " · Bakiye: " + moneyFmt(data.balance != null ? data.balance : bal),
						"ok"
					);
					if (uidEl) uidEl.value = "";
					if (balEl) balEl.value = "5000";
					resetCardUidLiveHint();
				})
				.catch(function () {
					showAlert("İstek başarısız.", "err");
				});
		});
	}

	var adminBtnModalDelete = document.getElementById("admin-btn-modal-delete");
	if (adminBtnModalDelete) {
		adminBtnModalDelete.addEventListener("click", function () {
			var id = document.getElementById("admin-prod-id").value.trim();
			if (!id || !confirm("Bu ürün pasifleştirilsin mi? (Satıştan kalkar.)")) {
				return;
			}
			fetch("/api/admin/products/" + encodeURIComponent(id), {
				method: "DELETE",
				headers: authHeaders(),
			})
				.then(function (r) {
					if (!r.ok) {
						showAlert("Pasifleştirilemedi.", "err");
						return;
					}
					showAlert("Ürün pasifleştirildi.", "ok");
					closeAdminProductModal();
					loadAdminProductCatalog();
				})
				.catch(function () {
					showAlert("İstek başarısız.", "err");
				});
		});
	}

	loadUsers();

	(function applyAdminUiScale() {
		if (document.documentElement.classList.contains("pos-perf")) {
			return;
		}
		var shell = document.getElementById("pos-main-scale");
		if (!shell) {
			return;
		}
		var st = getComputedStyle(document.documentElement).getPropertyValue("--pos-terminal-ui-scale").trim();
		var n = parseFloat(st);
		if (!isNaN(n) && n >= 0.4 && n <= 1.5) {
			shell.style.zoom = String(n);
		}
	})();
})();
