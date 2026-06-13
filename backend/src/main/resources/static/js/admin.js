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

	function clearAdminSession() {
		sessionStorage.removeItem(TOKEN_KEY);
		sessionStorage.removeItem("aqua_user");
		sessionStorage.removeItem(ROLE_KEY);
		sessionStorage.removeItem("aqua_display_name");
		sessionStorage.removeItem("aqua_sale_areas");
		sessionStorage.removeItem("aqua_ticket_sales");
		sessionStorage.removeItem("aqua_balance_load");
		sessionStorage.removeItem(ADMIN_PANEL_KEY);
	}

	function logoutToLogin() {
		clearAdminSession();
		window.location.replace("/index.html");
	}

	var adminLogoutBtn = document.getElementById("admin-logout-btn");
	if (adminLogoutBtn) {
		adminLogoutBtn.addEventListener("click", function (e) {
			e.preventDefault();
			logoutToLogin();
		});
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
			if (cb.disabled) {
				return;
			}
			out.push(cb.value);
		});
		return out;
	}

	function permBadgeForUser(u, field) {
		if (u.role === "ADMIN") {
			if (field === "admin") {
				return '<span class="badge ok">Evet</span>';
			}
			return "—";
		}
		var on = field === "ticket" ? u.ticketSalesAllowed !== false : field === "balance" ? u.balanceLoadAllowed !== false : !!u.adminPanelAccess;
		return on ? '<span class="badge ok">Evet</span>' : '<span class="badge off">Hayır</span>';
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
				permBadgeForUser(u, "ticket") +
				"</td>" +
				"<td>" +
				permBadgeForUser(u, "balance") +
				"</td>" +
				"<td>" +
				permBadgeForUser(u, "admin") +
				"</td>" +
				"<td>" +
				(u.role === "ADMIN" ? "—" : escapeHtml(formatSaleAreas(u.saleAreaCodes))) +
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
		if (u.role === "ADMIN") {
			pendingEditSaleCodes = [];
		}
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
		syncEditSaleAreaUi();
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
			saleAreaCodes: cRole === "ADMIN" || cRole === "TICKET" ? [] : collectSaleAreas(formCreate),
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
			saleAreaCodes: eRole === "ADMIN" || eRole === "TICKET" ? [] : collectSaleAreas(formEdit),
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
					var menus = Array.isArray(a.menuPageNames) ? a.menuPageNames.join(", ") : "—";
					if (!menus) {
						menus = "—";
					}
					var tr = document.createElement("tr");
					tr.innerHTML =
						"<td>" +
						escapeHtml(a.name || "") +
						"</td><td>" +
						escapeHtml(menus) +
						"</td><td>" +
						String(a.activeProductCount != null ? a.activeProductCount : 0) +
						'</td><td class="actions">' +
						'<button type="button" class="btn btn-ghost btn-sm" data-sa-edit="' +
						String(a.id) +
						'" data-sa-name="' +
						escapeAttr(a.name || "") +
						'" data-sa-menus="' +
						escapeAttr(JSON.stringify(a.menuPageIds || [])) +
						'">Düzenle</button></td>';
					tbody.appendChild(tr);
				});
			})
			.catch(function () {
				showAlert("Satış alanları yüklenemedi.", "err");
			});
	}

	function bumpAdminMenuPagesStale() {
		adminMenuPagesCache = [];
	}

	function loadMenuPagesPanel() {
		var tbody = document.getElementById("admin-menu-pages-tbody");
		var emptyEl = document.getElementById("admin-menu-pages-empty");
		if (!tbody) {
			return Promise.resolve();
		}
		hideAlert();
		return fetchAdminMenuPagesForModal()
			.then(function (pages) {
				pages = Array.isArray(pages) ? pages : [];
				adminMenuPagesCache = pages;
				if (!pages.length) {
					tbody.innerHTML = "";
					if (emptyEl) {
						emptyEl.hidden = false;
					}
					return pages;
				}
				if (emptyEl) {
					emptyEl.hidden = true;
				}
				tbody.innerHTML = "";
				pages.forEach(function (m) {
					var tr = document.createElement("tr");
					tr.innerHTML =
						"<td>" +
						escapeHtml(m.name || "") +
						"</td><td>" +
						String(m.productCount != null ? m.productCount : 0) +
						'</td><td class="actions">' +
						'<button type="button" class="btn btn-ghost btn-sm" data-mp-edit-modal="' +
						String(m.id) +
						'" data-mp-name="' +
						escapeAttr(m.name || "") +
						'" data-mp-code="' +
						escapeAttr(m.code || "") +
						'">Düzenle</button></td>';
					tbody.appendChild(tr);
				});
				return pages;
			})
			.catch(function () {
				showAlert("Menü başlıkları yüklenemedi.", "err");
			});
	}

	function refreshProductsPanelData() {
		return loadMenuPagesPanel().then(function () {
			loadAdminProductCatalog();
		});
	}

	function loadAdminProductCatalog() {
		var root = document.getElementById("admin-catalog-root");
		var emptyEl = document.getElementById("admin-catalog-empty");
		if (!root || !emptyEl) {
			return;
		}
		Promise.all([
			fetchAdminMenuPagesForModal(),
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
				var menus = Array.isArray(pair[0]) ? pair[0] : [];
				var products = pair[1];
				adminMenuPagesCache = menus;
				if (!products) {
					return;
				}
				if (!menus.length) {
					root.innerHTML = "";
					emptyEl.hidden = false;
					emptyEl.textContent = "Önce menü başlığı ekleyin.";
					return;
				}
				emptyEl.hidden = true;
				var byMenu = {};
				products.forEach(function (p) {
					var mid = p.menuPageId != null ? String(p.menuPageId) : "_none";
					if (!byMenu[mid]) {
						byMenu[mid] = [];
					}
					byMenu[mid].push(p);
				});
				root.innerHTML = "";
				menus.forEach(function (menu) {
					var list = byMenu[String(menu.id)] || [];
					var block = document.createElement("section");
					block.className = "admin-catalog-area";

					var head = document.createElement("div");
					head.className = "admin-catalog-area-head";
					var h3 = document.createElement("h3");
					h3.textContent = menu.name || menu.code || "Menü";
					head.appendChild(h3);
					var addInline = document.createElement("button");
					addInline.type = "button";
					addInline.className = "btn btn-ghost btn-sm";
					addInline.textContent = "+ Bu menüye ekle";
					addInline.addEventListener("click", function () {
						openAdminProductModal(null, menu.id);
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
						ph.textContent = "Bu menüde henüz ürün yok.";
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
		var selMp = document.getElementById("admin-prod-menu-page");
		if (!selMp) {
			return;
		}
		selMp.innerHTML = "";
		var pages = adminMenuPagesCache.slice().sort(function (a, b) {
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
			ph.textContent = "Önce menü başlığı ekleyin";
			selMp.appendChild(ph);
			selMp.disabled = true;
			if (hintMp) {
				hintMp.hidden = false;
				hintMp.textContent = "Ürün tanımları sekmesinden menü başlığı oluşturun.";
			}
			return;
		}
		selMp.disabled = false;
		if (hintMp) {
			hintMp.hidden = true;
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
		} else if (pages[0]) {
			selMp.value = String(pages[0].id);
		}
	}

	function openAdminProductModal(product, presetMenuPageId) {
		var modal = document.getElementById("admin-modal-product");
		var title = document.getElementById("admin-modal-product-title");
		var idEl = document.getElementById("admin-prod-id");
		var nameEl = document.getElementById("admin-prod-name");
		var priceEl = document.getElementById("admin-prod-price");
		var stockEl = document.getElementById("admin-prod-stock");
		var activeWrap = document.getElementById("admin-prod-active-wrap");
		var activeCb = document.getElementById("admin-prod-active");
		var delBtn = document.getElementById("admin-btn-modal-delete");
		if (!modal || !nameEl || !priceEl) {
			return;
		}
		fetchAdminMenuPagesForModal()
			.then(function (pages) {
				adminMenuPagesCache = Array.isArray(pages) ? pages : [];
				if (product) {
					title.textContent = "Ürünü düzenle";
					idEl.value = String(product.id);
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
					nameEl.value = "";
					priceEl.value = "";
					stockEl.value = "";
					activeWrap.hidden = true;
					delBtn.hidden = true;
					fillAdminProductMenuPageSelect(presetMenuPageId);
				}
				modal.hidden = false;
			})
			.catch(function () {
				showAlert("Menü başlıkları yüklenemedi.", "err");
			});
	}

	function closeAdminProductModal() {
		var modal = document.getElementById("admin-modal-product");
		if (modal) {
			modal.hidden = true;
		}
	}

	function renderSaleAreaMenuChecks(container, selectedIds) {
		if (!container) {
			return;
		}
		container.innerHTML = "";
		var selected = {};
		(selectedIds || []).forEach(function (id) {
			selected[String(id)] = true;
		});
		var menus = adminMenuPagesCache.slice().sort(function (a, b) {
			var oa = a.sortOrder != null ? a.sortOrder : 0;
			var ob = b.sortOrder != null ? b.sortOrder : 0;
			if (oa !== ob) {
				return oa - ob;
			}
			return (a.id || 0) - (b.id || 0);
		});
		if (!menus.length) {
			var p = document.createElement("p");
			p.className = "field-hint";
			p.textContent = "Henüz menü başlığı yok — Ürün tanımları sekmesinden ekleyin.";
			container.appendChild(p);
			return;
		}
		menus.forEach(function (m) {
			var lbl = document.createElement("label");
			lbl.className = "admin-check-card";
			var cb = document.createElement("input");
			cb.type = "checkbox";
			cb.value = String(m.id);
			cb.checked = !!selected[String(m.id)];
			var span = document.createElement("span");
			span.textContent = m.name || m.code || "Menü";
			lbl.appendChild(cb);
			lbl.appendChild(span);
			container.appendChild(lbl);
		});
	}

	function collectSaleAreaMenuIds(container) {
		if (!container) {
			return [];
		}
		return [].filter
			.call(container.querySelectorAll('input[type="checkbox"]:checked'), function (cb) {
				return cb.value;
			})
			.map(function (cb) {
				return Number(cb.value);
			})
			.filter(function (n) {
				return !isNaN(n);
			});
	}

	function openSaleAreaEditModal(id, name, menuPageIds) {
		var modal = document.getElementById("admin-modal-sale-area");
		var idEl = document.getElementById("modal-sa-id");
		var nameEl = document.getElementById("modal-sa-name");
		var checks = document.getElementById("modal-sa-menu-checks");
		if (!modal || !idEl || !nameEl) {
			return;
		}
		fetchAdminMenuPagesForModal()
			.then(function (pages) {
				adminMenuPagesCache = Array.isArray(pages) ? pages : [];
				idEl.value = String(id);
				nameEl.value = name || "";
				renderSaleAreaMenuChecks(checks, menuPageIds || []);
				modal.hidden = false;
				nameEl.focus();
				nameEl.select();
			})
			.catch(function () {
				showAlert("Menü listesi yüklenemedi.", "err");
			});
	}

	function closeSaleAreaEditModal() {
		var modal = document.getElementById("admin-modal-sale-area");
		if (modal) {
			modal.hidden = true;
		}
	}

	function openMenuPageEditModal(id, name, isGenel) {
		var modal = document.getElementById("admin-modal-menu-page");
		var idEl = document.getElementById("modal-mp-id");
		var nameEl = document.getElementById("modal-mp-name");
		var delBtn = document.getElementById("modal-mp-delete");
		var hint = document.getElementById("modal-mp-genel-hint");
		if (!modal || !idEl || !nameEl) {
			return;
		}
		idEl.value = String(id);
		nameEl.value = name || "";
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
			b.textContent = "+ Menü başlığı ekle";
		}
	}

	function openMenuPageFormForAdd() {
		var w = document.getElementById("mp-menu-page-form-wrap");
		var b = document.getElementById("btn-toggle-menu-page-form");
		if (!w || !b) {
			return;
		}
		closeSaleAreaFormWrap();
		w.hidden = false;
		b.textContent = "Formu kapat";
		requestAnimationFrame(function () {
			w.scrollIntoView({ behavior: "smooth", block: "nearest" });
			var nameIn = document.getElementById("mp-new-name");
			if (nameIn) {
				nameIn.focus();
			}
		});
	}

	function isMenuPageFormOpen() {
		var w = document.getElementById("mp-menu-page-form-wrap");
		return !!(w && !w.hidden);
	}

	var ADMIN_PANEL_IDS = [
		"users",
		"menu-pages",
		"products",
		"ticket-age-groups",
		"cards",
		"printer",
		"report-day-close",
		"report-general",
	];
	var AQUAPARK_MENU_PANELS = ["users", "menu-pages", "products", "ticket-age-groups"];
	var REPORT_MENU_PANELS = ["report-day-close", "report-general"];
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
		}
		if (id !== "products") {
			closeMenuPageFormWrap();
		}
		syncAdminNavForPanel(id);
		setAdminTabPanelVisible("admin-panel-users", id === "users");
		setAdminTabPanelVisible("admin-panel-products", id === "products");
		setAdminTabPanelVisible("admin-panel-menu-pages", id === "menu-pages");
		setAdminTabPanelVisible("admin-panel-cards", id === "cards");
		setAdminTabPanelVisible("admin-panel-printer", id === "printer");
		setAdminTabPanelVisible("admin-panel-ticket-age-groups", id === "ticket-age-groups");
		setAdminTabPanelVisible("admin-panel-report-day-close", id === "report-day-close");
		setAdminTabPanelVisible("admin-panel-report-general", id === "report-general");
		if (id === "menu-pages") {
			loadCategoriesPanel();
		}
			if (id === "products") {
				refreshProductsPanelData();
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

	function renderPaymentSalesSummary(containerEl, emptyEl, data) {
		if (!containerEl) {
			return;
		}
		containerEl.innerHTML = "";
		var grand = data && data.grandTotal != null ? parseFloat(String(data.grandTotal), 10) : 0;
		var agencyTicketTotal =
			data && data.agencyTicketTotalCount != null ? parseInt(String(data.agencyTicketTotalCount), 10) : 0;
		var hasData = (data && !isNaN(grand) && grand > 0) || (!isNaN(agencyTicketTotal) && agencyTicketTotal > 0);
		if (emptyEl) {
			emptyEl.hidden = hasData;
		}
		if (!data) {
			containerEl.hidden = true;
			return;
		}
		containerEl.hidden = false;
		var period =
			data.fromInclusive === data.toInclusive
				? String(data.fromInclusive || "")
				: (data.fromInclusive || "") + " → " + (data.toInclusive || "");
		var cards = [
			{ k: "Dönem", v: period },
			{ k: "Nakit", v: formatTryAmount(data.cashTotal) },
			{ k: "Kredi kartı", v: formatTryAmount(data.cardTotal) },
			{ k: "Acenta", v: formatTryAmount(data.agencyTotal) },
			{ k: "Toplam", v: formatTryAmount(data.grandTotal) },
		];
		if (Array.isArray(data.agencyTicketCounts)) {
			data.agencyTicketCounts.forEach(function (row) {
				if (!row || !row.name) {
					return;
				}
				cards.push({ k: row.name, v: String(row.count != null ? row.count : 0) + " adet" });
			});
		}
		if (!isNaN(agencyTicketTotal) && agencyTicketTotal > 0) {
			cards.push({ k: "Acenta bilet toplam", v: String(agencyTicketTotal) + " adet" });
		}
		cards.forEach(function (c) {
			var div = document.createElement("div");
			div.className = "admin-report-summary-card";
			div.innerHTML =
				"<span class=\"admin-report-summary-label\">" +
				escapeHtml(c.k) +
				"</span><strong>" +
				escapeHtml(c.v) +
				"</strong>";
			containerEl.appendChild(div);
		});
	}

	function fetchPaymentSalesReport(fromDate, toDate) {
		var q =
			"from=" +
			encodeURIComponent(fromDate) +
			"&to=" +
			encodeURIComponent(toDate);
		return fetch("/api/admin/reports/payment-sales?" + q, { headers: authHeaders() })
			.then(adminReportParseResponse)
			.then(function (x) {
				return adminReportJsonOrThrow(x.r, x.data).then(function () {
					return x.data;
				});
			});
	}

	function loadAdminReportDayClose() {
		var dateEl = document.getElementById("report-day-date");
		var sumEl = document.getElementById("report-day-summary");
		var empty = document.getElementById("report-day-empty");
		if (!dateEl || !sumEl) {
			return;
		}
		if (!dateEl.value) {
			dateEl.value = adminReportTodayIso();
		}
		hideAlert();
		fetchPaymentSalesReport(dateEl.value, dateEl.value)
			.then(function (data) {
				renderPaymentSalesSummary(sumEl, empty, data);
			})
			.catch(function () {});
	}

	function loadAdminReportGeneral() {
		var fromEl = document.getElementById("report-gen-from");
		var toEl = document.getElementById("report-gen-to");
		var cardsEl = document.getElementById("report-gen-summary-cards");
		var empty = document.getElementById("report-gen-empty");
		if (!fromEl || !toEl || !cardsEl) {
			return;
		}
		ensureReportRangeInputs(fromEl, toEl);
		hideAlert();
		fetchPaymentSalesReport(fromEl.value, toEl.value)
			.then(function (data) {
				renderPaymentSalesSummary(cardsEl, empty, data);
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

	function setSaleAreaCheckboxesState(containerId, disabled, clearWhenDisable) {
		var wrap = document.getElementById(containerId);
		if (!wrap) {
			return;
		}
		wrap.querySelectorAll('input[name="sale-area"]').forEach(function (cb) {
			cb.disabled = disabled;
			if (disabled && clearWhenDisable) {
				cb.checked = false;
			}
		});
	}

	function syncCreateSaleAreaUi() {
		var role = document.getElementById("c-role") && document.getElementById("c-role").value;
		var isAdmin = role === "ADMIN";
		var isTicket = role === "TICKET";
		setSaleAreaCheckboxesState("c-sale-area-checks", isAdmin || isTicket, isAdmin || isTicket);
	}

	function syncEditSaleAreaUi() {
		var role = document.getElementById("e-role") && document.getElementById("e-role").value;
		var isAdmin = role === "ADMIN";
		var isTicket = role === "TICKET";
		setSaleAreaCheckboxesState("e-sale-area-checks", isAdmin || isTicket, isAdmin || isTicket);
	}

	function syncCreateAdminPanelUi() {
		var sel = document.getElementById("c-role");
		var cb = document.getElementById("c-admin-panel");
		var tCk = document.getElementById("c-ticket");
		var bCk = document.getElementById("c-balance");
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
		if (bCk) {
			if (sel.value === "ADMIN") {
				bCk.checked = false;
				bCk.disabled = true;
			} else {
				bCk.disabled = false;
			}
		}
		syncCreateSaleAreaUi();
	}

	function syncEditAdminPanelUi() {
		var sel = document.getElementById("e-role");
		var cb = document.getElementById("e-admin-panel");
		var tCk = document.getElementById("e-ticket");
		var bCk = document.getElementById("e-balance");
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
		if (bCk) {
			if (sel.value === "ADMIN") {
				bCk.checked = false;
				bCk.disabled = true;
			} else {
				bCk.disabled = false;
			}
		}
		syncEditSaleAreaUi();
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

	function parsePrinterTargetOption(raw) {
		if (raw == null || raw === "") {
			return null;
		}
		try {
			var o = JSON.parse(raw);
			if (o && (o.kind === "windows" || o.kind === "serial") && o.name) {
				return o;
			}
		} catch (e) {}
		return null;
	}

	function loadPrinterPorts() {
		var sel = document.getElementById("printer-port");
		if (!sel) {
			return;
		}
		hideAlert();
		fetch("/api/printer/print-targets", { headers: authHeaders() })
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
			.then(function (targets) {
				if (!targets || !Array.isArray(targets)) {
					return;
				}
				var cur = sel.value;
				sel.innerHTML = "";
				var opt0 = document.createElement("option");
				opt0.value = "";
				var firstIsWin = targets.length > 0 && targets[0].kind === "windows";
				opt0.textContent = firstIsWin ? "— Yazıcı kuyruğu seçin —" : "— Port seçin —";
				sel.appendChild(opt0);
				targets.forEach(function (p) {
					var o = document.createElement("option");
					o.value = JSON.stringify({ kind: p.kind, name: p.name });
					o.textContent = p.name + (p.description ? " — " + p.description : "");
					sel.appendChild(o);
				});
				if (cur) {
					sel.value = cur;
				}
				return loadPrinterSettingsFromServer();
			})
			.then(function () {
				loadPrinterWindowsQueuesRef();
			})
			.catch(function (e) {
				if (e && e.message === "401") {
					return;
				}
				showAlert(
					(e && e.message) ||
						"Yazıcı hedef listesi alınamadı. Oturum açık mı? Sunucu güncel JAR ile mi çalışıyor?",
					"err"
				);
				loadPrinterWindowsQueuesRef();
			});
	}

	/** Windows: javax.print kuyruk adları (COM listesinden bağımsız). */
	function loadPrinterWindowsQueuesRef() {
		var el = document.getElementById("printer-windows-queues-ref");
		if (!el) {
			return;
		}
		el.textContent = "Yükleniyor…";
		fetch("/api/printer/windows-diagnostics", { headers: authHeaders() })
			.then(function (r) {
				if (r.status === 401) {
					return Promise.reject(new Error("401"));
				}
				return r.json();
			})
			.then(function (d) {
				if (!d || typeof d.isWindows === "undefined") {
					el.textContent = "Yanıt okunamadı.";
					return;
				}
				if (!d.isWindows) {
					el.textContent =
						"Bu API Windows üzerinde değil; kuyruk listesi burada doldurulmaz.\n\nÜstteki seri port listesi yalnızca COM aygıtlarıdır; Windows yazıcı kuyrukları ayrı bir mekanizmadır (APP_PRINTER_WINDOWS_QUEUE).";
					return;
				}
				var parts = [];
				parts.push(
					"Özet: Java’nın gördüğü Windows kuyrukları. Üstteki açılır liste Windows’ta genelde bu kuyrukları gösterir; kuyruk yoksa yalnızca COM listelenir."
				);
				if (d.configuredQueueName) {
					parts.push(
						"\nOrtamda ayarlı kuyruk: «" +
							d.configuredQueueName +
							"» — eşleşme: " +
							(d.configuredMatchesQueue ? "evet" : "hayır") +
							"."
					);
				}
				if (d.queues && d.queues.length) {
					parts.push("\nKuyruklar (" + d.queues.length + "): " + d.queues.join(", "));
				} else {
					parts.push(
						"\nJava şu an ham bayt (BYTE_ARRAY) ile eşleşen kuyruk döndürmedi; sürücü veya oturum kısıtı olabilir."
					);
				}
				el.textContent = parts.join("");
			})
			.catch(function (e) {
				if (e && e.message === "401") {
					return;
				}
				el.textContent = "Kuyruk özeti alınamadı (ağ veya sunucu).";
			});
	}

	/** Sunucuda kayıtlı varsayılan port/baud (liste dolduktan sonra). */
	function loadPrinterSettingsFromServer() {
		return fetch("/api/printer/settings", { headers: authHeaders() })
			.then(function (r) {
				if (r.status === 401) {
					sessionStorage.removeItem(TOKEN_KEY);
					window.location.replace("/index.html");
					return Promise.reject(new Error("401"));
				}
				if (!r.ok) {
					return Promise.resolve();
				}
				return r.json();
			})
			.then(function (s) {
				var notice = document.getElementById("printer-windows-notice");
				if (s && s.source === "windows-queue" && s.windowsQueueName) {
					if (notice) {
						notice.hidden = false;
						notice.textContent =
							"Bu sunucu PC’sinde Windows kuyruk modu aktif: «" +
							s.windowsQueueName +
							"». Test fişi COM seçmeden bu yazıcıya gider (ortam: APP_PRINTER_WINDOWS_QUEUE).";
					}
					var selW = document.getElementById("printer-port");
					if (selW && s.windowsQueueName) {
						var wn = String(s.windowsQueueName);
						Array.prototype.forEach.call(selW.options, function (o) {
							var tp = parsePrinterTargetOption(o.value);
							if (
								tp &&
								tp.kind === "windows" &&
								String(tp.name).toLowerCase() === wn.toLowerCase()
							) {
								selW.value = o.value;
							}
						});
					}
					return;
				}
				if (notice) {
					notice.hidden = true;
				}
				if (!s || !s.port) {
					return;
				}
				var baudEl = document.getElementById("printer-baud");
				if (baudEl && s.baudRate != null && !isNaN(s.baudRate)) {
					var bv = String(s.baudRate);
					var baudOk = Array.prototype.some.call(baudEl.options, function (o) {
						return o.value === bv;
					});
					if (baudOk) {
						baudEl.value = bv;
					}
				}
				var sel = document.getElementById("printer-port");
				var manualEl = document.getElementById("printer-port-manual");
				if (!sel) {
					return;
				}
				var wantPort = String(s.port);
				var matchedVal = null;
				Array.prototype.forEach.call(sel.options, function (o) {
					var tp = parsePrinterTargetOption(o.value);
					if (tp && tp.kind === "serial" && tp.name === wantPort) {
						matchedVal = o.value;
					}
				});
				if (matchedVal) {
					sel.value = matchedVal;
					if (manualEl) {
						manualEl.value = "";
					}
				} else if (manualEl) {
					manualEl.value = s.port;
				}
			})
			.catch(function () {});
	}

	function persistPrinterSettingsAfterTest(port, baud) {
		if (!isFullAdmin || !port) {
			return Promise.resolve({ skipped: true });
		}
		return fetch("/api/printer/settings", {
			method: "PUT",
			headers: authHeadersJson(),
			body: JSON.stringify({ port: port, baudRate: baud }),
		}).then(function (r) {
			return r
				.json()
				.then(function (data) {
					return { ok: r.ok, status: r.status, data: data };
				})
				.catch(function () {
					return { ok: r.ok, status: r.status, data: { error: "Yanıt okunamadı" } };
				});
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
			var sel = document.getElementById("printer-port");
			var parsed = parsePrinterTargetOption(sel ? sel.value.trim() : "");
			var baud = parseInt(document.getElementById("printer-baud").value, 10);
			var modeEl = document.getElementById("printer-mode");
			var mode = modeEl ? modeEl.value : "full";

			function buildTestPayload() {
				var p = { baudRate: baud, mode: mode };
				if (manual) {
					p.port = manual;
				} else if (parsed && parsed.kind === "windows") {
					p.windowsQueueName = parsed.name;
				} else if (parsed && parsed.kind === "serial") {
					p.port = parsed.name;
				}
				return p;
			}
			function hasPrintTargetPayload(p) {
				return !!(p && (p.port || p.windowsQueueName));
			}
			function runPrinterTest(payload) {
				if (!hasPrintTargetPayload(payload)) {
					showAlert(
						"Listeden yazıcı kuyruğu veya COM seçin. Elle adres için «Port (elle)» alanını kullanın (örn. COM3).",
						"err"
					);
					return;
				}
				hideAlert();
				var skipComSave = !!payload.windowsQueueName;
				fetch("/api/printer/test", {
					method: "POST",
					headers: authHeadersJson(),
					body: JSON.stringify(payload),
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
						function testOkMessage() {
							var base = (res.data && res.data.message) || "Test fişi gönderildi.";
							var h = res.data && res.data.hint;
							return h ? base + " " + h : base;
						}
						return persistPrinterSettingsAfterTest(
							skipComSave ? "" : (payload.port || ""),
							baud
						)
							.then(function (saveRes) {
								if (saveRes && saveRes.skipped) {
									showAlert(testOkMessage(), "ok");
									return;
								}
								if (!saveRes.ok) {
									showAlert(
										(res.data && res.data.message ? res.data.message : "Test fişi gönderildi.") +
											" Ancak sunucuya kaydedilemedi: " +
											((saveRes.data && saveRes.data.error) || "HTTP " + saveRes.status),
										"err"
									);
									return;
								}
								var extra =
									saveRes.data && saveRes.data.message ? saveRes.data.message + " " : "";
								showAlert(extra + testOkMessage(), "ok");
							})
							.catch(function () {
								showAlert(
									(res.data && res.data.message ? res.data.message : "Test fişi gönderildi.") +
										" Sunucuya kayıt sırasında ağ hatası.",
									"err"
								);
							});
					})
					.catch(function () {
						showAlert("İstek başarısız.", "err");
					});
			}

			var payload = buildTestPayload();
			if (hasPrintTargetPayload(payload)) {
				runPrinterTest(payload);
				return;
			}
			fetch("/api/printer/settings", { headers: authHeaders() })
				.then(function (r) {
					if (!r.ok) {
						return null;
					}
					return r.json();
				})
				.then(function (s) {
					if (s && s.source === "windows-queue" && s.windowsQueueName && !payload.port) {
						payload.windowsQueueName = s.windowsQueueName;
					}
					runPrinterTest(payload);
				})
				.catch(function () {
					runPrinterTest(payload);
				});
		});
	}
	var btnPrinterRefresh = document.getElementById("btn-printer-refresh");
	if (btnPrinterRefresh) {
		btnPrinterRefresh.addEventListener("click", function () {
			loadPrinterPorts();
		});
	}
	var btnPrinterWinDiag = document.getElementById("btn-printer-windows-diag");
	if (btnPrinterWinDiag) {
		btnPrinterWinDiag.addEventListener("click", function () {
			var out = document.getElementById("printer-windows-diag-out");
			hideAlert();
			if (out) {
				out.hidden = false;
				out.textContent = "Kontrol ediliyor…";
			}
			fetch("/api/printer/windows-diagnostics", { headers: authHeaders() })
				.then(function (r) {
					if (r.status === 401) {
						sessionStorage.removeItem(TOKEN_KEY);
						window.location.replace("/index.html");
						return Promise.reject(new Error("401"));
					}
					return r.json().then(function (data) {
						return { ok: r.ok, data: data };
					});
				})
				.then(function (res) {
					var d = res.data || {};
					var lines = [];
					lines.push(d.message || "(yanıt yok)");
					lines.push("");
					lines.push("OS: " + (d.osName != null ? d.osName : "?"));
					lines.push("Windows: " + (d.isWindows ? "evet" : "hayır"));
					lines.push("Yapılandırılan kuyruk: " + (d.configuredQueueName != null ? d.configuredQueueName : "(yok)"));
					lines.push("Eşleşme: " + (d.configuredMatchesQueue ? "evet" : "hayır"));
					lines.push("Kuyruk sayısı: " + (d.queueCount != null ? d.queueCount : 0));
					if (d.queues && d.queues.length) {
						lines.push("");
						lines.push("Java’nın gördüğü kuyruklar:");
						for (var i = 0; i < d.queues.length; i++) {
							lines.push("  · " + d.queues[i]);
						}
					}
					if (out) {
						out.textContent = lines.join("\n");
						out.hidden = false;
					}
					loadPrinterWindowsQueuesRef();
					showAlert(
						d.success ? "Kontrol tamam — detay aşağıda." : "Kontrol tamam — uyarı veya hata; detay aşağıda.",
						d.success ? "ok" : "err"
					);
				})
				.catch(function (e) {
					if (e && e.message === "401") {
						return;
					}
					if (out) {
						out.textContent = "İstek başarısız veya yanıt okunamadı.";
						out.hidden = false;
					}
					loadPrinterWindowsQueuesRef();
					showAlert("Windows kuyruk kontrolü başarısız.", "err");
				});
		});
	}

	var btnAdminCatalogAdd = document.getElementById("btn-admin-catalog-add");
	if (btnAdminCatalogAdd) {
		btnAdminCatalogAdd.addEventListener("click", function () {
			if (!adminMenuPagesCache.length) {
				openMenuPageFormForAdd();
				showAlert("Önce bir menü başlığı ekleyin (ör. Soğuk içecek), sonra ürün tanımlayın.", "ok");
				return;
			}
			openAdminProductModal(null, null);
		});
	}

	var adminFormProduct = document.getElementById("admin-form-product");
	if (adminFormProduct) {
		adminFormProduct.addEventListener("submit", function (e) {
			e.preventDefault();
			var pid = document.getElementById("admin-prod-id").value.trim();
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
				var menuIds = [];
				try {
					menuIds = JSON.parse(saBtn.getAttribute("data-sa-menus") || "[]");
				} catch (err) {
					menuIds = [];
				}
				openSaleAreaEditModal(
					Number(saBtn.getAttribute("data-sa-edit")),
					saBtn.getAttribute("data-sa-name") || "",
					menuIds
				);
			}
		});
	}

	var adminPanelProducts = document.getElementById("admin-panel-products");
	if (adminPanelProducts) {
		adminPanelProducts.addEventListener("click", function (e) {
			var mpBtn = e.target.closest("[data-mp-edit-modal]");
			if (mpBtn) {
				e.preventDefault();
				openMenuPageEditModal(
					Number(mpBtn.getAttribute("data-mp-edit-modal")),
					mpBtn.getAttribute("data-mp-name") || "",
					(mpBtn.getAttribute("data-mp-code") || "").toUpperCase() === "GENEL"
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
				body: JSON.stringify({
					name: nn,
					menuPageIds: collectSaleAreaMenuIds(document.getElementById("modal-sa-menu-checks")),
				}),
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
			fetch("/api/admin/menu-pages/" + encodeURIComponent(id), {
				method: "PUT",
				headers: authHeadersJson(),
				body: JSON.stringify({ name: nn }),
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
					refreshProductsPanelData();
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
					refreshProductsPanelData();
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
			"Mifare kartı okutun (8 veya 14 hex karakter). Okuyucu HID modunda hex veya ondalık gönderebilir.",
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
			var uidRaw = uidEl ? uidEl.value.trim() : "";
			var uid =
				typeof MifareUidUtil !== "undefined" ? MifareUidUtil.cleanUid(uidRaw) : uidRaw;
			var balRaw = balEl ? String(balEl.value).trim().replace(",", ".") : "";
			var bal = parseFloat(balRaw);
			if (!uid) {
				showAlert("Kart UID girin.", "err");
				return;
			}
			if (typeof MifareUidUtil !== "undefined" && !MifareUidUtil.isPlausible(uidRaw)) {
				showAlert("Geçersiz Mifare UID — 4 veya 7 bayt hex beklenir.", "err");
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
					if (balEl) balEl.value = "0";
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
		btnToggleMenuPageForm.addEventListener("click", function (e) {
			e.preventDefault();
			if (isMenuPageFormOpen()) {
				closeMenuPageFormWrap();
			} else {
				openMenuPageFormForAdd();
			}
		});
	}

	var formNewMenuPage = document.getElementById("form-new-menu-page");
	if (formNewMenuPage) {
		formNewMenuPage.addEventListener("submit", function (e) {
			e.preventDefault();
			hideAlert();
			var name = document.getElementById("mp-new-name").value.trim();
			if (!name) {
				showAlert("Menü adı zorunludur.", "err");
				return;
			}
			var body = { name: name };
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
						var errMsg = (res.data && (res.data.detail || res.data.message)) || "Eklenemedi";
						showAlert(errMsg, "err");
						return;
					}
					showAlert("Menü başlığı eklendi.", "ok");
					formNewMenuPage.reset();
					closeMenuPageFormWrap();
					bumpAdminMenuPagesStale();
					refreshProductsPanelData();
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
					showAlert("Satış alanı eklendi.", "ok");
					formNewCat.reset();
					closeSaleAreaFormWrap();
					bumpAdminMenuPagesStale();
					loadCategoriesPanel();
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
