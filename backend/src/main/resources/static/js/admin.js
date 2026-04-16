(function () {
	const TOKEN_KEY = "aqua_token";
	const ROLE_KEY = "aqua_role";
	const ADMIN_PANEL_KEY = "aqua_admin_panel";

	if (window.location.protocol === "file:") {
		window.alert(
			"Bu sayfayı dosya olarak (file://) açmayın.\n\nTarayıcı adres çubuğuna yazın:\nhttp://127.0.0.1:8081/admin.html\n\n(Aksi halde /api/... istekleri 404 Not Found verir.)"
		);
	}

	if (!sessionStorage.getItem(TOKEN_KEY)) {
		window.location.replace("/index.html");
		return;
	}
	var sessionRole = sessionStorage.getItem(ROLE_KEY) || "";
	var adminPanelSessionOk = sessionStorage.getItem(ADMIN_PANEL_KEY) === "true";
	var isFullAdmin = sessionRole === "ADMIN";
	if (!isFullAdmin && !adminPanelSessionOk) {
		window.location.replace("/pos");
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

	const roleLabels = {
		ADMIN: "Yönetici",
		SUPERVISOR: "Süpervizör",
		CASHIER: "Kasiyer",
		TICKET: "Bilet satış",
	};

	var saleAreaNameByCode = {};
	var lastAdminSaleAreas = [];
	var pendingEditSaleCodes = null;
	/** Ürün modalında satış alanı değişince doldurulur */
	var adminMenuPagesCache = [];

	function mergeSaleAreaNames(areas) {
		lastAdminSaleAreas = Array.isArray(areas) ? areas : [];
		lastAdminSaleAreas.forEach(function (a) {
			if (a && a.code) {
				saleAreaNameByCode[a.code] = a.name || a.code;
			}
		});
	}

	function formatSaleAreas(codes) {
		if (!codes || !codes.length) {
			return "—";
		}
		return codes
			.map(function (c) {
				return saleAreaNameByCode[c] || c;
			})
			.join(", ");
	}

	function fetchAdminSaleAreas() {
		return fetch("/api/admin/sale-areas", { headers: authHeaders() }).then(function (r) {
			if (r.status === 401) {
				window.location.replace("/index.html");
				return Promise.reject(new Error("401"));
			}
			if (r.status === 403) {
				throw new Error("403");
			}
			if (!r.ok) {
				throw new Error("http");
			}
			return r.json();
		});
	}

	function renderSaleAreaCheckboxes(container, selectedCodes) {
		if (!container) {
			return;
		}
		container.innerHTML = "";
		var sel = selectedCodes || [];
		lastAdminSaleAreas.forEach(function (a) {
			var lab = document.createElement("label");
			var cb = document.createElement("input");
			cb.type = "checkbox";
			cb.name = "sale-area";
			cb.value = a.code;
			cb.checked = sel.indexOf(a.code) >= 0;
			lab.appendChild(cb);
			lab.appendChild(document.createTextNode(" "));
			var sp = document.createElement("span");
			sp.textContent = a.name || a.code;
			lab.appendChild(sp);
			container.appendChild(lab);
		});
	}

	function refreshUserSaleAreaUi() {
		var cChecks = document.getElementById("c-sale-area-checks");
		var eChecks = document.getElementById("e-sale-area-checks");
		var keepCreate = collectSaleAreas(formCreate);
		return fetchAdminSaleAreas()
			.then(function (areas) {
				mergeSaleAreaNames(areas);
				renderSaleAreaCheckboxes(cChecks, keepCreate);
				if (!editPanel.hidden && pendingEditSaleCodes) {
					renderSaleAreaCheckboxes(eChecks, pendingEditSaleCodes);
				} else {
					renderSaleAreaCheckboxes(eChecks, []);
				}
			})
			.catch(function () {
				showAlert("Satış alanları yüklenemedi.", "err");
			});
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
				"<td>" +
				(u.adminPanelAccess
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
		pendingEditSaleCodes = Array.isArray(u.saleAreaCodes) ? u.saleAreaCodes.slice() : [];
		renderSaleAreaCheckboxes(document.getElementById("e-sale-area-checks"), pendingEditSaleCodes);
		document.getElementById("e-ticket").checked = u.ticketSalesAllowed !== false;
		document.getElementById("e-balance").checked = u.balanceLoadAllowed !== false;
		var eAp = document.getElementById("e-admin-panel");
		if (eAp) {
			eAp.checked = !!u.adminPanelGranted;
			eAp.disabled = u.role === "ADMIN";
			if (u.role === "ADMIN") {
				eAp.checked = true;
			}
		}
		syncEditAdminPanelUi();
		editPanel.hidden = false;
		editPanel.scrollIntoView({ behavior: "smooth", block: "nearest" });
	}

	document.getElementById("btn-edit-cancel").addEventListener("click", function () {
		editPanel.hidden = true;
		pendingEditSaleCodes = null;
		renderSaleAreaCheckboxes(document.getElementById("e-sale-area-checks"), []);
	});

	formCreate.addEventListener("submit", function (e) {
		e.preventDefault();
		hideAlert();
		var cRole = document.getElementById("c-role").value;
		var body = {
			userId: document.getElementById("c-userId").value.trim(),
			password: document.getElementById("c-password").value,
			displayName: document.getElementById("c-displayName").value.trim() || null,
			role: cRole,
			saleAreaCodes: collectSaleAreas(formCreate),
			ticketSalesAllowed: document.getElementById("c-ticket").checked,
			balanceLoadAllowed: document.getElementById("c-balance").checked,
			adminPanelAccess:
				cRole === "ADMIN" ||
				!!(document.getElementById("c-admin-panel") && document.getElementById("c-admin-panel").checked),
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
				syncCreateAdminPanelUi();
				refreshUserSaleAreaUi().finally(function () {
				loadUsers();
					loadCategoriesPanel();
				});
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
		var eRole = document.getElementById("e-role").value;
		var body = {
			displayName: document.getElementById("e-displayName").value.trim() || null,
			role: eRole,
			active: document.getElementById("e-active").checked,
			saleAreaCodes: collectSaleAreas(formEdit),
			ticketSalesAllowed: document.getElementById("e-ticket").checked,
			balanceLoadAllowed: document.getElementById("e-balance").checked,
			adminPanelAccess:
				eRole === "ADMIN" ||
				!!(document.getElementById("e-admin-panel") && document.getElementById("e-admin-panel").checked),
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
				pendingEditSaleCodes = collectSaleAreas(formEdit);
				editPanel.hidden = true;
				refreshUserSaleAreaUi().finally(function () {
				loadUsers();
					loadCategoriesPanel();
				});
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

	function loadCategoriesPanel() {
		var tbody = document.getElementById("admin-categories-tbody");
		var emptyEl = document.getElementById("admin-categories-empty");
		if (!tbody) {
			return;
		}
		hideAlert();
		fetchAdminSaleAreas()
			.then(function (areas) {
				mergeSaleAreaNames(areas);
				if (!areas.length) {
					tbody.innerHTML = "";
					if (emptyEl) emptyEl.hidden = false;
					return;
				}
				if (emptyEl) emptyEl.hidden = true;
				tbody.innerHTML = "";
				areas.forEach(function (a) {
					var tr = document.createElement("tr");
					tr.innerHTML =
						"<td>" +
						escapeHtml(a.name || "") +
						"</td><td>" +
						String(a.activeProductCount != null ? a.activeProductCount : 0) +
						"</td><td>" +
						String(a.totalProductCount != null ? a.totalProductCount : 0) +
						'</td><td class="actions">' +
						'<button type="button" class="btn btn-ghost btn-sm" data-sa-edit="' +
						String(a.id) +
						'" data-sa-name="' +
						escapeAttr(a.name || "") +
						'">Düzenle</button></td>';
					tbody.appendChild(tr);
				});
			})
			.catch(function () {
				showAlert("Kategoriler yüklenemedi.", "err");
			});
	}

	function bumpAdminMenuPagesStale() {
		adminMenuPagesCache = [];
	}

	function loadMenuPagesPanel() {
		var tbody = document.getElementById("admin-menu-pages-tbody");
		var emptyEl = document.getElementById("admin-menu-pages-empty");
		var saleSel = document.getElementById("mp-new-sale-area");
		if (!tbody || !saleSel) {
			return;
		}
		hideAlert();
		Promise.all([fetchAdminSaleAreas(), fetchAdminMenuPagesForModal()])
			.then(function (pair) {
				var areas = pair[0] || [];
				var pages = Array.isArray(pair[1]) ? pair[1] : [];
				mergeSaleAreaNames(areas);
				saleSel.innerHTML = "";
				if (!areas.length) {
					var ph0 = document.createElement("option");
					ph0.value = "";
					ph0.textContent = "— Önce Kategoriler’den satış alanı ekleyin —";
					ph0.disabled = true;
					saleSel.appendChild(ph0);
				} else {
					areas.forEach(function (a) {
						var o = document.createElement("option");
						o.value = a.code;
						o.textContent = a.name || a.code;
						saleSel.appendChild(o);
					});
				}
				if (!pages.length) {
					tbody.innerHTML = "";
					if (emptyEl) {
						emptyEl.hidden = false;
					}
					return;
				}
				if (emptyEl) {
					emptyEl.hidden = true;
				}
				tbody.innerHTML = "";
				pages.forEach(function (m) {
					var tr = document.createElement("tr");
					var areaLabel = saleAreaNameByCode[m.saleAreaCode] || m.saleAreaCode || "";
					var isGenel = String(m.code || "").toUpperCase() === "GENEL";
					tr.innerHTML =
						"<td>" +
						escapeHtml(areaLabel + " (" + (m.saleAreaCode || "") + ")") +
						"</td><td>" +
						escapeHtml(m.name || "") +
						"</td><td>" +
						String(m.sortOrder != null ? m.sortOrder : 0) +
						"</td><td>" +
						String(m.productCount != null ? m.productCount : 0) +
						'</td><td class="actions">' +
						'<button type="button" class="btn btn-ghost btn-sm" data-mp-edit-modal="' +
						String(m.id) +
						'" data-mp-name="' +
						escapeAttr(m.name || "") +
						'" data-mp-sort="' +
						String(m.sortOrder != null ? m.sortOrder : 0) +
						'" data-mp-genel="' +
						(isGenel ? "1" : "0") +
						'">Düzenle</button></td>';
					tbody.appendChild(tr);
				});
			})
			.catch(function () {
				showAlert("Menü sayfaları yüklenemedi.", "err");
			});
	}

	function loadAdminProductCatalog() {
		var root = document.getElementById("admin-catalog-root");
		var emptyEl = document.getElementById("admin-catalog-empty");
		if (!root || !emptyEl) {
			return;
		}
		Promise.all([
			fetchAdminSaleAreas().then(function (areas) {
				mergeSaleAreaNames(areas);
				return areas;
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
		return fetchAdminSaleAreas().then(function (areas) {
			mergeSaleAreaNames(areas);
			return areas;
		});
	}

	function fetchAdminMenuPagesForModal() {
		return fetch("/api/admin/menu-pages", { headers: authHeaders() }).then(function (r) {
			if (r.status === 401) {
				window.location.replace("/index.html");
				return Promise.reject(new Error("401"));
			}
			if (!r.ok) {
				throw new Error("http");
			}
			return r.json();
		});
	}

	function fillAdminProductMenuPageSelect(preferredMenuPageId) {
		var selArea = document.getElementById("admin-prod-sale-area");
		var selMp = document.getElementById("admin-prod-menu-page");
		if (!selMp || !selArea) {
			return;
		}
		var code = selArea.value;
		selMp.innerHTML = "";
		var pages = adminMenuPagesCache
			.filter(function (m) {
				return m.saleAreaCode === code;
			})
			.sort(function (a, b) {
				var oa = a.sortOrder != null ? a.sortOrder : 0;
				var ob = b.sortOrder != null ? b.sortOrder : 0;
				if (oa !== ob) {
					return oa - ob;
				}
				return (a.id || 0) - (b.id || 0);
			});
		var hintMp = document.getElementById("admin-prod-menu-page-hint");
		if (!pages.length) {
			var ph = document.createElement("option");
			ph.value = "";
			ph.textContent = "Önce bu alan için menü sayfası ekleyin";
			selMp.appendChild(ph);
			selMp.disabled = true;
			if (hintMp) {
				hintMp.hidden = false;
				hintMp.textContent =
					"Bu satış alanı için menü sayfası yok; Menü sayfaları sekmesinden ekleyin. (Adet sınırı yok — liste yalnızca seçilen alana göre filtrelenir.)";
			}
			return;
		}
		selMp.disabled = false;
		if (hintMp) {
			hintMp.hidden = false;
			hintMp.textContent =
				"Adet sınırı yok. Bu alana tanımlı " +
				pages.length +
				" menü sayfası listeleniyor; diğer satış alanlarının sayfaları burada görünmez (önce satış alanını değiştirin). Uzun listelerde açılır kutuyu kaydırın.";
		}
		pages.forEach(function (m) {
			var o = document.createElement("option");
			o.value = String(m.id);
			o.textContent = m.name || "Menü";
			selMp.appendChild(o);
		});
		var want = preferredMenuPageId != null ? String(preferredMenuPageId) : "";
		if (want && [].some.call(selMp.options, function (opt) { return opt.value === want; })) {
			selMp.value = want;
			return;
		}
		var gen = pages.find(function (x) {
			return String(x.code || "").toUpperCase() === "GENEL";
		});
		selMp.value = String((gen || pages[0]).id);
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
		Promise.all([fetchSaleAreasForAdminModal(), fetchAdminMenuPagesForModal()])
			.then(function (tuple) {
				var areas = tuple[0];
				adminMenuPagesCache = Array.isArray(tuple[1]) ? tuple[1] : [];
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
					fillAdminProductMenuPageSelect(product.menuPageId);
				} else {
					title.textContent = "Yeni ürün";
					idEl.value = "";
					sel.value = presetAreaCode || (areas[0] && areas[0].code) || "";
					nameEl.value = "";
					priceEl.value = "";
					stockEl.value = "";
					activeWrap.hidden = true;
					delBtn.hidden = true;
					fillAdminProductMenuPageSelect(null);
				}
				modal.hidden = false;
			})
			.catch(function () {
				showAlert("Satış alanları veya menü sayfaları yüklenemedi.", "err");
			});
	}

	function closeAdminProductModal() {
		var modal = document.getElementById("admin-modal-product");
		if (modal) {
			modal.hidden = true;
		}
	}

	function openSaleAreaEditModal(id, name) {
		var modal = document.getElementById("admin-modal-sale-area");
		var idEl = document.getElementById("modal-sa-id");
		var nameEl = document.getElementById("modal-sa-name");
		if (!modal || !idEl || !nameEl) {
			return;
		}
		idEl.value = String(id);
		nameEl.value = name || "";
		modal.hidden = false;
		nameEl.focus();
		nameEl.select();
	}

	function closeSaleAreaEditModal() {
		var modal = document.getElementById("admin-modal-sale-area");
		if (modal) {
			modal.hidden = true;
		}
	}

	function openMenuPageEditModal(id, name, sortStr, isGenel) {
		var modal = document.getElementById("admin-modal-menu-page");
		var idEl = document.getElementById("modal-mp-id");
		var nameEl = document.getElementById("modal-mp-name");
		var sortEl = document.getElementById("modal-mp-sort");
		var delBtn = document.getElementById("modal-mp-delete");
		var hint = document.getElementById("modal-mp-genel-hint");
		if (!modal || !idEl || !nameEl || !sortEl) {
			return;
		}
		idEl.value = String(id);
		nameEl.value = name || "";
		sortEl.value = String(sortStr != null ? sortStr : "0");
		if (delBtn) {
			delBtn.hidden = !!isGenel;
		}
		if (hint) {
			hint.hidden = !isGenel;
		}
		modal.hidden = false;
		nameEl.focus();
		nameEl.select();
	}

	function closeMenuPageEditModal() {
		var modal = document.getElementById("admin-modal-menu-page");
		if (modal) {
			modal.hidden = true;
		}
	}

	function closeSaleAreaFormWrap() {
		var w = document.getElementById("mp-sale-area-form-wrap");
		var b = document.getElementById("btn-toggle-sale-area-form");
		if (w) {
			w.hidden = true;
		}
		if (b) {
			b.textContent = "+ Satış alanı ekle";
		}
	}

	function closeMenuPageFormWrap() {
		var w = document.getElementById("mp-menu-page-form-wrap");
		var b = document.getElementById("btn-toggle-menu-page-form");
		if (w) {
			w.hidden = true;
		}
		if (b) {
			b.textContent = "+ Menü sayfası ekle";
		}
	}

	var ADMIN_PANEL_IDS = [
		"users",
		"menu-pages",
		"products",
		"ticket-age-groups",
		"cards",
		"printer",
		"report-sale-areas",
		"report-products",
		"report-day-close",
		"report-general",
	];
	var AQUAPARK_MENU_PANELS = ["users", "menu-pages", "products", "ticket-age-groups"];
	var REPORT_MENU_PANELS = ["report-sale-areas", "report-products", "report-day-close", "report-general"];
	var REPORT_TX_LABELS = {
		SALE: "Ürün satışı (kart)",
		ENTRY: "Turnike / giriş",
		LOAD_CASH: "Nakit yükleme",
		LOAD_CARD: "Kartla yükleme",
		LOAD_AGENCY: "Acenta yükleme",
		TICKET_CASH: "Bilet tahsilatı (nakit)",
		TICKET_CARD: "Bilet tahsilatı (kart)",
		TICKET_CREDIT: "Bilet tahsilatı (kredili)",
		REFUND_CASH: "Nakit iade",
	};

	function isReportPanel(panelId) {
		return REPORT_MENU_PANELS.indexOf(panelId) >= 0;
	}

	function isAquaparkPanel(panelId) {
		return AQUAPARK_MENU_PANELS.indexOf(panelId) >= 0;
	}

	function closeAquaparkMenu() {
		var wrap = document.getElementById("admin-aquapark-wrap");
		var btn = document.getElementById("btn-admin-aquapark");
		if (wrap) {
			wrap.classList.remove("is-open");
		}
		if (btn) {
			btn.setAttribute("aria-expanded", "false");
		}
	}

	function closeReportMenu() {
		var wrap = document.getElementById("admin-report-wrap");
		var btn = document.getElementById("btn-admin-report");
		if (wrap) {
			wrap.classList.remove("is-open");
		}
		if (btn) {
			btn.setAttribute("aria-expanded", "false");
		}
	}

	function syncAdminNavForPanel(id) {
		document.querySelectorAll(".admin-tab[data-panel]").forEach(function (t) {
			if (t.hidden) {
				return;
			}
			var pid = t.getAttribute("data-panel");
			var on = pid === id;
				t.classList.toggle("active", on);
				t.setAttribute("aria-selected", on ? "true" : "false");
			});
		var aquaBtn = document.getElementById("btn-admin-aquapark");
		if (aquaBtn) {
			aquaBtn.classList.toggle("active", isAquaparkPanel(id));
		}
		document.querySelectorAll(".admin-aquapark-item").forEach(function (item) {
			if (item.hidden) {
				return;
			}
			var pid = item.getAttribute("data-panel");
			var on = pid === id;
			item.classList.toggle("admin-aquapark-item--current", on);
			if (on) {
				item.setAttribute("aria-current", "page");
			} else {
				item.removeAttribute("aria-current");
			}
		});
		var reportBtn = document.getElementById("btn-admin-report");
		if (reportBtn) {
			reportBtn.classList.toggle("active", isReportPanel(id));
		}
		document.querySelectorAll(".admin-report-item").forEach(function (item) {
			if (item.hidden) {
				return;
			}
			var pid = item.getAttribute("data-panel");
			var on = pid === id;
			item.classList.toggle("admin-report-item--current", on);
			if (on) {
				item.setAttribute("aria-current", "page");
			} else {
				item.removeAttribute("aria-current");
			}
		});
		closeAquaparkMenu();
		closeReportMenu();
	}

	function normalizeAdminHashFragment() {
		var h = (location.hash || "").replace(/^#/, "").trim();
		return h.replace(/^\/+/, "").toLowerCase();
	}

	function canAccessAdminPanel(panelId) {
		if (ADMIN_PANEL_IDS.indexOf(panelId) < 0) {
			return false;
		}
		if (isReportPanel(panelId)) {
			return isFullAdmin;
		}
		if (isFullAdmin) {
			return true;
		}
		return panelId === "menu-pages" || panelId === "ticket-age-groups";
	}

	function parseAdminPanelFromLocation() {
		var raw = normalizeAdminHashFragment();
		if (raw === "") {
			return isFullAdmin ? "users" : "menu-pages";
		}
		if (ADMIN_PANEL_IDS.indexOf(raw) < 0) {
			return isFullAdmin ? "users" : "menu-pages";
		}
		if (!canAccessAdminPanel(raw)) {
			return isFullAdmin ? "users" : "menu-pages";
		}
		return raw;
	}

	function writeAdminPanelHash(panelId) {
		var base = location.pathname + location.search;
		var next = base + "#" + panelId;
		var cur = base + (location.hash || "");
		if (cur !== next) {
			history.replaceState(null, "", next);
		}
	}

	/**
	 * Sekme + panel gösterimi. URL: admin.html#products — yenilemede aynı sekme kalır.
	 * @param {object} [opts]
	 * @param {boolean} [opts.noWriteHash] — true: yalnızca hashchange / programatik senkron (sonsuz döngü önlemi)
	 */
	function activateAdminPanel(panelId, opts) {
		opts = opts || {};
		var id = panelId;
		if (!canAccessAdminPanel(id)) {
			id = isFullAdmin ? "users" : "menu-pages";
		}
		if (id !== "menu-pages") {
			closeSaleAreaFormWrap();
			closeMenuPageFormWrap();
		}
		syncAdminNavForPanel(id);
		setAdminTabPanelVisible("admin-panel-users", id === "users");
		setAdminTabPanelVisible("admin-panel-products", id === "products");
		setAdminTabPanelVisible("admin-panel-menu-pages", id === "menu-pages");
		setAdminTabPanelVisible("admin-panel-cards", id === "cards");
		setAdminTabPanelVisible("admin-panel-printer", id === "printer");
		setAdminTabPanelVisible("admin-panel-ticket-age-groups", id === "ticket-age-groups");
		setAdminTabPanelVisible("admin-panel-report-sale-areas", id === "report-sale-areas");
		setAdminTabPanelVisible("admin-panel-report-products", id === "report-products");
		setAdminTabPanelVisible("admin-panel-report-day-close", id === "report-day-close");
		setAdminTabPanelVisible("admin-panel-report-general", id === "report-general");
		if (id === "menu-pages") {
			loadCategoriesPanel();
			loadMenuPagesPanel();
		}
			if (id === "products") {
				loadAdminProductCatalog();
			}
		if (id === "printer") {
			loadPrinterPorts();
		}
		if (id === "ticket-age-groups") {
			loadTicketAgeGroupsPanel();
		}
		if (id === "cards") {
			var uidIn = document.getElementById("card-issue-uid");
			if (uidIn) {
				uidIn.focus();
			}
		}
		if (id === "report-sale-areas") {
			loadAdminReportSaleAreas();
		}
		if (id === "report-products") {
			loadAdminReportProducts();
		}
		if (id === "report-day-close") {
			loadAdminReportDayClose();
		}
		if (id === "report-general") {
			loadAdminReportGeneral();
		}
		if (!opts.noWriteHash) {
			writeAdminPanelHash(id);
		}
	}

	/** Sekme gövdesi — `.admin-panel--visible` + `hidden` (admin.css !important ile zoom uyumu) */
	function setAdminTabPanelVisible(panelId, visible) {
		var el = document.getElementById(panelId);
		if (!el) {
			return;
		}
		if (visible) {
			el.classList.add("admin-panel--visible");
			el.removeAttribute("hidden");
			el.hidden = false;
		} else {
			el.classList.remove("admin-panel--visible");
			el.setAttribute("hidden", "");
			el.hidden = true;
		}
	}

	function adminReportDefaultRange() {
		var t = new Date();
		var to = t.toISOString().slice(0, 10);
		var f = new Date(t.getTime() - 6 * 86400000);
		return { from: f.toISOString().slice(0, 10), to: to };
	}

	function adminReportTodayIso() {
		return new Date().toISOString().slice(0, 10);
	}

	function formatTryAmount(v) {
		if (v === null || v === undefined) {
			return "—";
		}
		var n = typeof v === "number" ? v : parseFloat(String(v), 10);
		if (isNaN(n)) {
			return "—";
		}
		return n.toLocaleString("tr-TR", { minimumFractionDigits: 2, maximumFractionDigits: 2 }) + " ₺";
	}

	function reportTxnTypeLabel(t) {
		return REPORT_TX_LABELS[t] || t || "—";
	}

	function ensureReportRangeInputs(fromEl, toEl) {
		if (!fromEl || !toEl) {
			return;
		}
		var r = adminReportDefaultRange();
		if (!fromEl.value) {
			fromEl.value = r.from;
		}
		if (!toEl.value) {
			toEl.value = r.to;
		}
	}

	function adminReportJsonOrThrow(r, data) {
		if (r.status === 401) {
			window.location.replace("/index.html");
			return Promise.reject(new Error("401"));
		}
		if (!r.ok) {
			var msg =
				(data && (data.message || data.detail || data.error || data.title)) ||
				(r.status === 404
					? "Rapor sunucusu bulunamadı (404). API’yi içeren sürüm çalışmıyor olabilir: backend’i son kodla yeniden derleyip (mvn clean package veya IDE Build) sunucuyu yeniden başlatın."
					: "Rapor yüklenemedi (HTTP " + r.status + ")");
			showAlert(msg, "err");
			return Promise.reject(new Error("http"));
		}
		return Promise.resolve(data);
	}

	/** HTML hata sayfası gibi JSON olmayan gövdelerde bile güvenli okuma */
	function adminReportParseResponse(r) {
		return r.text().then(function (text) {
			var data = null;
			var t = (text || "").trim();
			if (t.startsWith("{") || t.startsWith("[")) {
				try {
					data = JSON.parse(text);
				} catch (e) {
					data = null;
				}
			}
			return { r: r, data: data };
		});
	}

	function loadAdminReportSaleAreas() {
		var fromEl = document.getElementById("report-area-from");
		var toEl = document.getElementById("report-area-to");
		var tbody = document.getElementById("report-area-tbody");
		var empty = document.getElementById("report-area-empty");
		if (!fromEl || !toEl || !tbody) {
			return;
		}
		ensureReportRangeInputs(fromEl, toEl);
		hideAlert();
		var q = "from=" + encodeURIComponent(fromEl.value) + "&to=" + encodeURIComponent(toEl.value);
		fetch("/api/admin/reports/sales-by-sale-area?" + q, { headers: authHeaders() })
			.then(adminReportParseResponse)
			.then(function (x) {
				return adminReportJsonOrThrow(x.r, x.data).then(function () {
					return x.data;
				});
			})
			.then(function (rows) {
				tbody.innerHTML = "";
				if (!rows || !rows.length) {
					empty.hidden = false;
					return;
				}
				empty.hidden = true;
				rows.forEach(function (row) {
					var tr = document.createElement("tr");
					tr.innerHTML =
						"<td>" +
						escapeHtml(row.saleAreaCode || "") +
						"</td><td>" +
						escapeHtml(row.saleAreaName || "") +
						"</td><td>" +
						escapeHtml(String(row.saleLineCount != null ? row.saleLineCount : "")) +
						"</td><td>" +
						escapeHtml(formatTryAmount(row.revenueTry)) +
						"</td>";
					tbody.appendChild(tr);
				});
			})
			.catch(function () {});
	}

	function loadAdminReportProducts() {
		var fromEl = document.getElementById("report-prod-from");
		var toEl = document.getElementById("report-prod-to");
		var tbody = document.getElementById("report-prod-tbody");
		var empty = document.getElementById("report-prod-empty");
		if (!fromEl || !toEl || !tbody) {
			return;
		}
		ensureReportRangeInputs(fromEl, toEl);
		hideAlert();
		var q = "from=" + encodeURIComponent(fromEl.value) + "&to=" + encodeURIComponent(toEl.value);
		fetch("/api/admin/reports/sales-by-product?" + q, { headers: authHeaders() })
			.then(adminReportParseResponse)
			.then(function (x) {
				return adminReportJsonOrThrow(x.r, x.data).then(function () {
					return x.data;
				});
			})
			.then(function (rows) {
				tbody.innerHTML = "";
				if (!rows || !rows.length) {
					empty.hidden = false;
					return;
				}
				empty.hidden = true;
				rows.forEach(function (row) {
					var tr = document.createElement("tr");
					tr.innerHTML =
						"<td>" +
						escapeHtml(row.productName || "") +
						"</td><td>" +
						escapeHtml(String(row.saleLineCount != null ? row.saleLineCount : "")) +
						"</td><td>" +
						escapeHtml(formatTryAmount(row.revenueTry)) +
						"</td>";
					tbody.appendChild(tr);
				});
			})
			.catch(function () {});
	}

	function loadAdminReportDayClose() {
		var dateEl = document.getElementById("report-day-date");
		var sumEl = document.getElementById("report-day-summary");
		var tbody = document.getElementById("report-day-tbody");
		var empty = document.getElementById("report-day-empty");
		if (!dateEl || !sumEl || !tbody) {
			return;
		}
		if (!dateEl.value) {
			dateEl.value = adminReportTodayIso();
		}
		hideAlert();
		var q = "date=" + encodeURIComponent(dateEl.value) + "&limit=500";
		fetch("/api/admin/reports/day-close?" + q, { headers: authHeaders() })
			.then(adminReportParseResponse)
			.then(function (x) {
				return adminReportJsonOrThrow(x.r, x.data).then(function () {
					return x.data;
				});
			})
			.then(function (payload) {
				var sum = payload.summary;
				sumEl.innerHTML = "";
				if (sum) {
					sumEl.hidden = false;
					var cards = [
						{ k: "Dönem", v: (sum.fromInclusive || "") + " → " + (sum.toInclusive || "") },
						{ k: "Ürün satış geliri", v: formatTryAmount(sum.productSaleRevenueTotal) },
						{ k: "Satış satırı", v: String(sum.productSaleLineCount != null ? sum.productSaleLineCount : "0") },
					];
					cards.forEach(function (c) {
						var div = document.createElement("div");
						div.className = "admin-report-summary-card";
						div.innerHTML =
							"<span class=\"admin-report-summary-label\">" +
							escapeHtml(c.k) +
							"</span><strong>" +
							escapeHtml(c.v) +
							"</strong>";
						sumEl.appendChild(div);
					});
				} else {
					sumEl.hidden = true;
				}
				tbody.innerHTML = "";
				var lines = payload.lines || [];
				if (!lines.length) {
					empty.hidden = false;
					return;
				}
				empty.hidden = true;
				lines.forEach(function (line) {
					var tr = document.createElement("tr");
					var prod = line.productName || "—";
					if (line.saleAreaName) {
						prod = prod + " · " + line.saleAreaName;
					}
					var tStr = line.createdAt ? String(line.createdAt).replace("T", " ").replace("Z", "") : "—";
					tr.innerHTML =
						"<td>" +
						escapeHtml(tStr) +
						"</td><td>" +
						escapeHtml(reportTxnTypeLabel(line.type)) +
						"</td><td>" +
						escapeHtml(formatTryAmount(line.amountChange)) +
						"</td><td>" +
						escapeHtml(formatTryAmount(line.balanceAfter)) +
						"</td><td>" +
						escapeHtml(prod) +
						"</td><td>" +
						escapeHtml(line.cardUidMasked || "—") +
						"</td><td>" +
						escapeHtml((line.description || "").slice(0, 120)) +
						"</td>";
					tbody.appendChild(tr);
				});
			})
			.catch(function () {});
	}

	function loadAdminReportGeneral() {
		var fromEl = document.getElementById("report-gen-from");
		var toEl = document.getElementById("report-gen-to");
		var tbody = document.getElementById("report-gen-tbody");
		var cardsEl = document.getElementById("report-gen-summary-cards");
		if (!fromEl || !toEl || !tbody || !cardsEl) {
			return;
		}
		ensureReportRangeInputs(fromEl, toEl);
		hideAlert();
		var q = "from=" + encodeURIComponent(fromEl.value) + "&to=" + encodeURIComponent(toEl.value);
		fetch("/api/admin/reports/summary?" + q, { headers: authHeaders() })
			.then(adminReportParseResponse)
			.then(function (x) {
				return adminReportJsonOrThrow(x.r, x.data).then(function () {
					return x.data;
				});
			})
			.then(function (sum) {
				tbody.innerHTML = "";
				cardsEl.innerHTML = "";
				var saleRev = sum.productSaleRevenueTotal;
				var saleLines = sum.productSaleLineCount;
				cardsEl.hidden = false;
				[
					{ k: "Dönem", v: (sum.fromInclusive || "") + " → " + (sum.toInclusive || "") },
					{ k: "Ürün satış geliri", v: formatTryAmount(saleRev) },
					{ k: "Satış satırı", v: String(saleLines != null ? saleLines : "0") },
				].forEach(function (c) {
					var div = document.createElement("div");
					div.className = "admin-report-summary-card";
					div.innerHTML =
						"<span class=\"admin-report-summary-label\">" +
						escapeHtml(c.k) +
						"</span><strong>" +
						escapeHtml(c.v) +
						"</strong>";
					cardsEl.appendChild(div);
				});
				var rows = sum.byTransactionType || [];
				rows.forEach(function (row) {
					var tr = document.createElement("tr");
					tr.innerHTML =
						"<td>" +
						escapeHtml(reportTxnTypeLabel(row.type)) +
						"</td><td>" +
						escapeHtml(String(row.lineCount != null ? row.lineCount : "")) +
						"</td><td>" +
						escapeHtml(formatTryAmount(row.amountChangeSum)) +
						"</td>";
					tbody.appendChild(tr);
				});
			})
			.catch(function () {});
	}

	function applyRestrictedAdminEntry() {
		if (isFullAdmin) {
			return;
		}
		var sub = document.getElementById("admin-header-sub");
		if (sub) {
			sub.textContent =
				"Menü sayfaları, satış alanları ve bilet yaş grupları (kısıtlı erişim). Kullanıcı, ürün, kart, raporlama ve yazıcı yalnızca tam yöneticidedir.";
		}
		["tab-cards", "tab-printer"].forEach(function (tid) {
			var el = document.getElementById(tid);
			if (el) {
				el.hidden = true;
				el.classList.remove("active");
				el.setAttribute("aria-selected", "false");
			}
		});
		AQUAPARK_MENU_PANELS.forEach(function (pid) {
			var el = document.querySelector(".admin-aquapark-item[data-panel=\"" + pid + "\"]");
			if (el) {
				el.hidden = !(pid === "menu-pages" || pid === "ticket-age-groups");
			}
		});
		var reportWrap = document.getElementById("admin-report-wrap");
		if (reportWrap) {
			reportWrap.hidden = true;
		}
		activateAdminPanel(parseAdminPanelFromLocation(), { noWriteHash: false });
	}

	function syncCreateAdminPanelUi() {
		var sel = document.getElementById("c-role");
		var cb = document.getElementById("c-admin-panel");
		var tCk = document.getElementById("c-ticket");
		if (!sel || !cb) {
			return;
		}
		if (sel.value === "ADMIN") {
			cb.checked = true;
			cb.disabled = true;
		} else {
			cb.disabled = false;
		}
		if (tCk) {
			if (sel.value === "ADMIN" || sel.value === "TICKET") {
				tCk.checked = true;
				tCk.disabled = true;
			} else {
				tCk.disabled = false;
			}
		}
	}

	function syncEditAdminPanelUi() {
		var sel = document.getElementById("e-role");
		var cb = document.getElementById("e-admin-panel");
		var tCk = document.getElementById("e-ticket");
		if (!sel || !cb) {
			return;
		}
		if (sel.value === "ADMIN") {
			cb.checked = true;
			cb.disabled = true;
		} else {
			cb.disabled = false;
		}
		if (tCk) {
			if (sel.value === "ADMIN" || sel.value === "TICKET") {
				tCk.checked = true;
				tCk.disabled = true;
			} else {
				tCk.disabled = false;
			}
		}
	}

	var cRoleEl = document.getElementById("c-role");
	if (cRoleEl) {
		cRoleEl.addEventListener("change", syncCreateAdminPanelUi);
		syncCreateAdminPanelUi();
	}
	var eRoleEl = document.getElementById("e-role");
	if (eRoleEl) {
		eRoleEl.addEventListener("change", syncEditAdminPanelUi);
	}

	document.querySelectorAll(".admin-tab").forEach(function (tab) {
		tab.addEventListener("click", function () {
			var id = tab.getAttribute("data-panel");
			if (!id) {
				return;
			}
			if (!isFullAdmin && id !== "menu-pages" && id !== "ticket-age-groups") {
				return;
			}
			activateAdminPanel(id);
		});
	});

	var aquaWrap = document.getElementById("admin-aquapark-wrap");
	var aquaBtn = document.getElementById("btn-admin-aquapark");
	if (aquaBtn && aquaWrap) {
		aquaBtn.addEventListener("click", function (e) {
			e.stopPropagation();
			var open = !aquaWrap.classList.contains("is-open");
			aquaWrap.classList.toggle("is-open", open);
			aquaBtn.setAttribute("aria-expanded", open ? "true" : "false");
		});
	}

	document.querySelectorAll(".admin-aquapark-item").forEach(function (item) {
		item.addEventListener("click", function () {
			var id = item.getAttribute("data-panel");
			if (!id) {
				return;
			}
			if (!isFullAdmin && id !== "menu-pages" && id !== "ticket-age-groups") {
				return;
			}
			activateAdminPanel(id);
		});
	});

	var reportWrapNav = document.getElementById("admin-report-wrap");
	var reportBtnNav = document.getElementById("btn-admin-report");
	if (reportBtnNav && reportWrapNav) {
		reportBtnNav.addEventListener("click", function (e) {
			e.stopPropagation();
			if (!isFullAdmin) {
				return;
			}
			var open = !reportWrapNav.classList.contains("is-open");
			reportWrapNav.classList.toggle("is-open", open);
			reportBtnNav.setAttribute("aria-expanded", open ? "true" : "false");
		});
	}

	document.querySelectorAll(".admin-report-item").forEach(function (item) {
		item.addEventListener("click", function () {
			var id = item.getAttribute("data-panel");
			if (!id || !isFullAdmin) {
				return;
			}
			activateAdminPanel(id);
		});
	});

	var btnReportArea = document.getElementById("btn-report-area-refresh");
	if (btnReportArea) {
		btnReportArea.addEventListener("click", function () {
			loadAdminReportSaleAreas();
		});
	}
	var btnReportProd = document.getElementById("btn-report-prod-refresh");
	if (btnReportProd) {
		btnReportProd.addEventListener("click", function () {
			loadAdminReportProducts();
		});
	}
	var btnReportDay = document.getElementById("btn-report-day-refresh");
	if (btnReportDay) {
		btnReportDay.addEventListener("click", function () {
			loadAdminReportDayClose();
		});
	}
	var btnReportGen = document.getElementById("btn-report-gen-refresh");
	if (btnReportGen) {
		btnReportGen.addEventListener("click", function () {
			loadAdminReportGeneral();
		});
	}

	document.addEventListener("click", function (e) {
		var wrap = document.getElementById("admin-aquapark-wrap");
		if (wrap && wrap.classList.contains("is-open") && !wrap.contains(e.target)) {
			closeAquaparkMenu();
		}
		var rw = document.getElementById("admin-report-wrap");
		if (rw && rw.classList.contains("is-open") && !rw.contains(e.target)) {
			closeReportMenu();
		}
	});

	document.addEventListener("keydown", function (e) {
		if (e.key === "Escape") {
			closeAquaparkMenu();
			closeReportMenu();
		}
	});

	window.addEventListener("hashchange", function () {
		activateAdminPanel(parseAdminPanelFromLocation(), { noWriteHash: true });
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

	function formatTagPrice(v) {
		var n = Number(v);
		if (isNaN(n)) {
			n = 0;
		}
		return n.toLocaleString("tr-TR", { minimumFractionDigits: 2, maximumFractionDigits: 2 });
	}

	function resetTicketAgeForm() {
		var hid = document.getElementById("tag-edit-id");
		var nm = document.getElementById("tag-name");
		var pr = document.getElementById("tag-price");
		var so = document.getElementById("tag-sort");
		var ac = document.getElementById("tag-active");
		var saveBtn = document.getElementById("btn-tag-save");
		if (hid) hid.value = "";
		if (nm) nm.value = "";
		if (pr) pr.value = "";
		if (so) so.value = "";
		if (ac) ac.checked = true;
		if (saveBtn) saveBtn.textContent = "Kaydet";
	}

	function startEditTicketAge(row) {
		var hid = document.getElementById("tag-edit-id");
		var nm = document.getElementById("tag-name");
		var pr = document.getElementById("tag-price");
		var so = document.getElementById("tag-sort");
		var ac = document.getElementById("tag-active");
		var saveBtn = document.getElementById("btn-tag-save");
		if (hid) hid.value = String(row.id);
		if (nm) {
			nm.value = row.name || "";
			nm.focus();
		}
		if (pr) pr.value = row.price != null ? String(row.price) : "";
		if (so) so.value = row.sortOrder != null ? String(row.sortOrder) : "";
		if (ac) ac.checked = !!row.active;
		if (saveBtn) saveBtn.textContent = "Güncelle";
	}

	function renderTicketAgeGroupsTable(rows) {
		var tbody = document.getElementById("ticket-age-tbody");
		var empty = document.getElementById("ticket-age-empty");
		if (!tbody) {
			return;
		}
		tbody.innerHTML = "";
		var list = Array.isArray(rows) ? rows.slice() : [];
		list.sort(function (a, b) {
			var sa = a.sortOrder != null ? a.sortOrder : 0;
			var sb = b.sortOrder != null ? b.sortOrder : 0;
			if (sa !== sb) {
				return sa - sb;
			}
			return (a.id || 0) - (b.id || 0);
		});
		list.forEach(function (row) {
			var tr = document.createElement("tr");
			var tdS = document.createElement("td");
			tdS.textContent = row.sortOrder != null ? String(row.sortOrder) : "—";
			var tdN = document.createElement("td");
			tdN.textContent = row.name || "";
			var tdP = document.createElement("td");
			tdP.textContent = formatTagPrice(row.price);
			var tdA = document.createElement("td");
			tdA.textContent = row.active ? "Evet" : "Hayır";
			var tdX = document.createElement("td");
			tdX.style.whiteSpace = "nowrap";
			var btnE = document.createElement("button");
			btnE.type = "button";
			btnE.className = "btn btn-ghost btn-sm";
			btnE.textContent = "Düzenle";
			btnE.addEventListener("click", function () {
				startEditTicketAge(row);
			});
			var btnD = document.createElement("button");
			btnD.type = "button";
			btnD.className = "btn btn-ghost btn-sm";
			btnD.textContent = "Sil";
			btnD.addEventListener("click", function () {
				deleteTicketAgeGroup(row.id);
			});
			tdX.appendChild(btnE);
			tdX.appendChild(document.createTextNode(" "));
			tdX.appendChild(btnD);
			tr.appendChild(tdS);
			tr.appendChild(tdN);
			tr.appendChild(tdP);
			tr.appendChild(tdA);
			tr.appendChild(tdX);
			tbody.appendChild(tr);
		});
		if (empty) {
			empty.hidden = list.length > 0;
		}
	}

	function loadTicketAgeGroupsPanel() {
		var tbody = document.getElementById("ticket-age-tbody");
		if (!tbody) {
			return;
		}
		hideAlert();
		fetch("/api/admin/ticket-age-groups", { headers: authHeaders() })
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
			.then(function (list) {
				renderTicketAgeGroupsTable(Array.isArray(list) ? list : []);
			})
			.catch(function (e) {
				if (e && e.message === "401") {
					return;
				}
				showAlert((e && e.message) || "Tarifeler yüklenemedi.", "err");
			});
	}

	function deleteTicketAgeGroup(id) {
		if (!window.confirm("Bu tarifeyi silmek istediğinize emin misiniz?")) {
			return;
		}
		hideAlert();
		fetch("/api/admin/ticket-age-groups/" + encodeURIComponent(String(id)), {
			method: "DELETE",
			headers: authHeaders(),
		})
			.then(function (r) {
				if (r.status === 401) {
					sessionStorage.removeItem(TOKEN_KEY);
					window.location.replace("/index.html");
					return Promise.reject(new Error("401"));
				}
				if (!(r.ok || r.status === 204)) {
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
			})
			.then(function () {
				showAlert("Silindi.", "ok");
				loadTicketAgeGroupsPanel();
				resetTicketAgeForm();
			})
			.catch(function (e) {
				if (e && e.message === "401") {
					return;
				}
				showAlert((e && e.message) || "Silinemedi.", "err");
			});
	}

	var formTicketAge = document.getElementById("form-ticket-age");
	if (formTicketAge) {
		formTicketAge.addEventListener("submit", function (ev) {
			ev.preventDefault();
			var hid = document.getElementById("tag-edit-id");
			var editId = hid && hid.value ? String(hid.value).trim() : "";
			var nameEl = document.getElementById("tag-name");
			var priceEl = document.getElementById("tag-price");
			var sortEl = document.getElementById("tag-sort");
			var actEl = document.getElementById("tag-active");
			var name = nameEl ? nameEl.value.trim() : "";
			var price = priceEl ? parseFloat(String(priceEl.value).replace(",", "."), 10) : NaN;
			var sortStr = sortEl ? sortEl.value.trim() : "";
			var sortOrder = sortStr === "" ? null : parseInt(sortStr, 10);
			var active = actEl ? !!actEl.checked : true;
			if (!name) {
				showAlert("Yaş grubu adı gerekli.", "err");
				return;
			}
			if (isNaN(price) || price < 0) {
				showAlert("Geçerli bir fiyat girin.", "err");
				return;
			}
			if (editId) {
				if (sortStr === "" || isNaN(sortOrder)) {
					showAlert("Düzenlemede liste sırası (tam sayı) gerekli.", "err");
					return;
				}
			} else if (sortStr !== "" && isNaN(sortOrder)) {
				showAlert("Liste sırası tam sayı olmalı veya boş bırakın.", "err");
				return;
			}
			hideAlert();
			var url = "/api/admin/ticket-age-groups";
			var method = "POST";
			var body;
			if (editId) {
				url += "/" + encodeURIComponent(editId);
				method = "PUT";
				body = JSON.stringify({
					name: name,
					price: price,
					sortOrder: sortOrder,
					active: active,
				});
			} else {
				body = JSON.stringify({
					name: name,
					price: price,
					sortOrder: sortOrder,
					active: active,
				});
			}
			fetch(url, { method: method, headers: authHeadersJson(), body: body })
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
				.then(function () {
					showAlert(editId ? "Güncellendi." : "Kaydedildi.", "ok");
					resetTicketAgeForm();
					loadTicketAgeGroupsPanel();
				})
				.catch(function (e) {
					if (e && e.message === "401") {
						return;
					}
					showAlert((e && e.message) || "Kayıt başarısız.", "err");
				});
		});
	}
	var btnTagReset = document.getElementById("btn-tag-reset");
	if (btnTagReset) {
		btnTagReset.addEventListener("click", function () {
			resetTicketAgeForm();
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
			var menuPageRaw = document.getElementById("admin-prod-menu-page").value.trim();
			var menuPageId = parseInt(menuPageRaw, 10);
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
			if (!menuPageRaw || isNaN(menuPageId)) {
				showAlert("Menü sayfası seçin (yoksa yönetimden menü sayfası oluşturun).", "err");
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
						menuPageId: menuPageId,
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
						bumpAdminMenuPagesStale();
						closeAdminProductModal();
						loadAdminProductCatalog();
						loadCategoriesPanel();
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
						menuPageId: menuPageId,
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
						bumpAdminMenuPagesStale();
						closeAdminProductModal();
						loadAdminProductCatalog();
						loadCategoriesPanel();
					})
					.catch(function () {
						showAlert("İstek başarısız.", "err");
					});
			}
		});
	}

	var adminProdSaleArea = document.getElementById("admin-prod-sale-area");
	if (adminProdSaleArea) {
		adminProdSaleArea.addEventListener("change", function () {
			fillAdminProductMenuPageSelect(null);
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

	var adminPanelMenuPages = document.getElementById("admin-panel-menu-pages");
	if (adminPanelMenuPages) {
		adminPanelMenuPages.addEventListener("click", function (e) {
			var saBtn = e.target.closest("[data-sa-edit]");
			if (saBtn) {
				e.preventDefault();
				openSaleAreaEditModal(
					Number(saBtn.getAttribute("data-sa-edit")),
					saBtn.getAttribute("data-sa-name") || ""
				);
				return;
			}
			var mpBtn = e.target.closest("[data-mp-edit-modal]");
			if (mpBtn) {
				e.preventDefault();
				openMenuPageEditModal(
					Number(mpBtn.getAttribute("data-mp-edit-modal")),
					mpBtn.getAttribute("data-mp-name") || "",
					mpBtn.getAttribute("data-mp-sort") || "0",
					mpBtn.getAttribute("data-mp-genel") === "1"
				);
			}
		});
	}

	var formSaleAreaModal = document.getElementById("admin-form-sale-area");
	if (formSaleAreaModal) {
		formSaleAreaModal.addEventListener("submit", function (e) {
			e.preventDefault();
			var idEl = document.getElementById("modal-sa-id");
			var nameEl = document.getElementById("modal-sa-name");
			var id = idEl ? Number(idEl.value) : NaN;
			var nn = nameEl ? String(nameEl.value).trim() : "";
			if (!nn) {
				showAlert("Ad boş olamaz.", "err");
				return;
			}
			if (isNaN(id)) {
				showAlert("Geçersiz kayıt.", "err");
				return;
			}
			fetch("/api/admin/sale-areas/" + encodeURIComponent(id), {
				method: "PUT",
				headers: authHeadersJson(),
				body: JSON.stringify({ name: nn }),
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
						showAlert(data.detail || "Güncellenemedi", "err");
						return;
					}
					showAlert("Satış alanı güncellendi.", "ok");
					closeSaleAreaEditModal();
					loadCategoriesPanel();
					loadMenuPagesPanel();
					refreshUserSaleAreaUi();
					loadUsers();
					loadAdminProductCatalog();
				})
				.catch(function () {
					showAlert("İstek başarısız.", "err");
				});
		});
	}

	var btnModalSaDelete = document.getElementById("modal-sa-delete");
	if (btnModalSaDelete) {
		btnModalSaDelete.addEventListener("click", function () {
			var idEl = document.getElementById("modal-sa-id");
			var nameEl = document.getElementById("modal-sa-name");
			var id = idEl ? Number(idEl.value) : NaN;
			var nm = nameEl && nameEl.value.trim() ? nameEl.value.trim() : "Bu satış alanı";
			if (isNaN(id)) {
				return;
			}
			if (!confirm("«" + nm + "» satış alanı silinsin mi? (İçinde ürün varsa silinmez.)")) {
				return;
			}
			fetch("/api/admin/sale-areas/" + encodeURIComponent(id), {
				method: "DELETE",
				headers: authHeaders(),
			})
				.then(async function (r) {
					if (r.status === 401) {
						window.location.replace("/index.html");
						return;
					}
					if (!r.ok) {
						var data = await r.json().catch(function () {
							return {};
						});
						showAlert(data.detail || "Silinemedi", "err");
						return;
					}
					showAlert("Satış alanı silindi.", "ok");
					closeSaleAreaEditModal();
					loadCategoriesPanel();
					loadMenuPagesPanel();
					refreshUserSaleAreaUi();
					loadUsers();
					loadAdminProductCatalog();
				})
				.catch(function () {
					showAlert("İstek başarısız.", "err");
				});
		});
	}

	var btnModalSaCancel = document.getElementById("modal-sa-cancel");
	var backdropSa = document.getElementById("admin-modal-sale-area-backdrop");
	if (btnModalSaCancel) {
		btnModalSaCancel.addEventListener("click", closeSaleAreaEditModal);
	}
	if (backdropSa) {
		backdropSa.addEventListener("click", closeSaleAreaEditModal);
	}

	var formMenuPageModal = document.getElementById("admin-form-menu-page");
	if (formMenuPageModal) {
		formMenuPageModal.addEventListener("submit", function (e) {
			e.preventDefault();
			var idEl = document.getElementById("modal-mp-id");
			var nameEl = document.getElementById("modal-mp-name");
			var sortEl = document.getElementById("modal-mp-sort");
			var id = idEl ? Number(idEl.value) : NaN;
			var nn = nameEl ? String(nameEl.value).trim() : "";
			var sn = sortEl ? parseInt(String(sortEl.value).trim(), 10) : NaN;
			if (!nn) {
				showAlert("Ad boş olamaz.", "err");
				return;
			}
			if (isNaN(sn)) {
				showAlert("Sıra geçerli bir tam sayı olmalı.", "err");
				return;
			}
			if (isNaN(id)) {
				showAlert("Geçersiz kayıt.", "err");
				return;
			}
			fetch("/api/admin/menu-pages/" + encodeURIComponent(id), {
				method: "PUT",
				headers: authHeadersJson(),
				body: JSON.stringify({ name: nn, sortOrder: sn }),
			})
				.then(function (r) {
					if (r.status === 401) {
						window.location.replace("/index.html");
						return null;
					}
					return r.json().then(function (data) {
						return { ok: r.ok, data: data };
					});
				})
				.then(function (res) {
					if (!res) {
						return;
					}
					if (!res.ok) {
						showAlert((res.data && res.data.detail) || "Güncellenemedi", "err");
						return;
					}
					showAlert("Menü sayfası güncellendi.", "ok");
					closeMenuPageEditModal();
					bumpAdminMenuPagesStale();
					loadMenuPagesPanel();
				})
				.catch(function () {
					showAlert("İstek başarısız.", "err");
				});
		});
	}

	var btnModalMpDelete = document.getElementById("modal-mp-delete");
	if (btnModalMpDelete) {
		btnModalMpDelete.addEventListener("click", function () {
			if (btnModalMpDelete.hidden) {
				return;
			}
			var idEl = document.getElementById("modal-mp-id");
			var id = idEl ? Number(idEl.value) : NaN;
			if (isNaN(id)) {
				return;
			}
			fetch("/api/admin/menu-pages/" + encodeURIComponent(id), {
				method: "DELETE",
				headers: authHeaders(),
			})
				.then(async function (r) {
					if (r.status === 401) {
						window.location.replace("/index.html");
						return;
					}
					if (!r.ok) {
						var data = await r.json().catch(function () {
							return {};
						});
						showAlert(data.detail || "Silinemedi", "err");
						return;
					}
					showAlert("Menü sayfası silindi.", "ok");
					closeMenuPageEditModal();
					bumpAdminMenuPagesStale();
					loadMenuPagesPanel();
				})
				.catch(function () {
					showAlert("İstek başarısız.", "err");
				});
		});
	}

	var btnModalMpCancel = document.getElementById("modal-mp-cancel");
	var backdropMp = document.getElementById("admin-modal-menu-page-backdrop");
	if (btnModalMpCancel) {
		btnModalMpCancel.addEventListener("click", closeMenuPageEditModal);
	}
	if (backdropMp) {
		backdropMp.addEventListener("click", closeMenuPageEditModal);
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
					loadCategoriesPanel();
				})
				.catch(function () {
					showAlert("İstek başarısız.", "err");
				});
		});
	}

	var btnToggleSaleAreaForm = document.getElementById("btn-toggle-sale-area-form");
	var mpSaleAreaFormWrap = document.getElementById("mp-sale-area-form-wrap");
	if (btnToggleSaleAreaForm && mpSaleAreaFormWrap) {
		btnToggleSaleAreaForm.addEventListener("click", function () {
			var willOpen = mpSaleAreaFormWrap.hidden;
			if (willOpen) {
				closeMenuPageFormWrap();
			}
			mpSaleAreaFormWrap.hidden = !willOpen;
			btnToggleSaleAreaForm.textContent = willOpen ? "Formu kapat" : "+ Satış alanı ekle";
			if (willOpen) {
				var c = document.getElementById("cat-name");
				if (c) {
					setTimeout(function () {
						c.focus();
					}, 30);
				}
			}
		});
	}

	var btnToggleMenuPageForm = document.getElementById("btn-toggle-menu-page-form");
	var mpMenuPageFormWrap = document.getElementById("mp-menu-page-form-wrap");
	if (btnToggleMenuPageForm && mpMenuPageFormWrap) {
		btnToggleMenuPageForm.addEventListener("click", function () {
			var willOpen = mpMenuPageFormWrap.hidden;
			if (willOpen) {
				closeSaleAreaFormWrap();
			}
			mpMenuPageFormWrap.hidden = !willOpen;
			btnToggleMenuPageForm.textContent = willOpen ? "Formu kapat" : "+ Menü sayfası ekle";
			if (willOpen) {
				var sel = document.getElementById("mp-new-sale-area");
				if (sel) {
					setTimeout(function () {
						sel.focus();
					}, 30);
				}
			}
		});
	}

	var formNewMenuPage = document.getElementById("form-new-menu-page");
	if (formNewMenuPage) {
		formNewMenuPage.addEventListener("submit", function (e) {
			e.preventDefault();
			hideAlert();
			var saleAreaCode = document.getElementById("mp-new-sale-area").value.trim();
			var name = document.getElementById("mp-new-name").value.trim();
			var sortRaw = document.getElementById("mp-new-sort").value.trim();
			if (!saleAreaCode || !name) {
				showAlert("Satış alanı ve ad zorunludur.", "err");
				return;
			}
			var body = { saleAreaCode: saleAreaCode, name: name };
			if (sortRaw !== "") {
				var so = parseInt(sortRaw, 10);
				if (!isNaN(so)) {
					body.sortOrder = so;
				}
			}
			fetch("/api/admin/menu-pages", {
				method: "POST",
				headers: authHeadersJson(),
				body: JSON.stringify(body),
			})
				.then(function (r) {
					if (r.status === 401) {
						window.location.replace("/index.html");
						return null;
					}
					return r.json().then(function (data) {
						return { ok: r.ok, data: data };
					});
				})
				.then(function (res) {
					if (!res) {
						return;
					}
					if (!res.ok) {
						showAlert((res.data && res.data.detail) || "Eklenemedi", "err");
						return;
					}
					showAlert("Menü sayfası eklendi.", "ok");
					formNewMenuPage.reset();
					closeMenuPageFormWrap();
					bumpAdminMenuPagesStale();
					loadMenuPagesPanel();
				})
				.catch(function () {
					showAlert("İstek başarısız.", "err");
				});
		});
	}

	var formNewCat = document.getElementById("form-new-category");
	if (formNewCat) {
		formNewCat.addEventListener("submit", function (e) {
			e.preventDefault();
			hideAlert();
			var name = document.getElementById("cat-name").value.trim();
			if (!name) {
				showAlert("Ad girin.", "err");
				return;
			}
			fetch("/api/admin/sale-areas", {
				method: "POST",
				headers: authHeadersJson(),
				body: JSON.stringify({ name: name }),
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
						showAlert(data.detail || "Eklenemedi", "err");
						return;
					}
					showAlert("Kategori eklendi.", "ok");
					formNewCat.reset();
					closeSaleAreaFormWrap();
					bumpAdminMenuPagesStale();
					loadCategoriesPanel();
					loadMenuPagesPanel();
					refreshUserSaleAreaUi().finally(function () {
	loadUsers();
					});
				})
				.catch(function () {
					showAlert("İstek başarısız.", "err");
				});
		});
	}

	fetchAdminSaleAreas()
		.then(function (areas) {
			mergeSaleAreaNames(areas);
			renderSaleAreaCheckboxes(document.getElementById("c-sale-area-checks"), []);
			renderSaleAreaCheckboxes(document.getElementById("e-sale-area-checks"), []);
			if (isFullAdmin) {
				loadUsers();
			} else {
				applyRestrictedAdminEntry();
			}
			if (isFullAdmin) {
				activateAdminPanel(parseAdminPanelFromLocation());
			}
		})
		.catch(function () {
			showAlert("Satış alanları yüklenemedi; kullanıcı formları eksik kalabilir.", "err");
			if (isFullAdmin) {
				loadUsers();
			} else {
				applyRestrictedAdminEntry();
			}
			if (isFullAdmin) {
				activateAdminPanel(parseAdminPanelFromLocation());
			}
		});

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
