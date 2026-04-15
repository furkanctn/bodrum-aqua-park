(function () {
	const form = document.getElementById("login-form");
	const alertEl = document.getElementById("login-alert");
	const btn = document.getElementById("login-btn");
	const userIdInput = document.getElementById("userId");
	const passwordInput = document.getElementById("password");
	const keyboardEl = document.getElementById("login-keyboard");

	/** @type {'userId' | 'password'} */
	let activeField = "userId";

	/** Telefon düzeni: 1-9, sonra ⌫ · 0 · onay */
	const NUM_ROWS = [
		["1", "2", "3"],
		["4", "5", "6"],
		["7", "8", "9"],
	];

	function showError(msg) {
		alertEl.textContent = msg;
		alertEl.classList.add("visible", "error");
	}

	function hideError() {
		alertEl.textContent = "";
		alertEl.classList.remove("visible", "error");
	}

	function getActiveInput() {
		return activeField === "userId" ? userIdInput : passwordInput;
	}

	function setActiveField(field) {
		activeField = field;
		userIdInput.closest(".field").classList.toggle("field-active", field === "userId");
		passwordInput.closest(".field").classList.toggle("field-active", field === "password");
		getActiveInput().focus({ preventScroll: true });
	}

	function appendDigit(d) {
		const input = getActiveInput();
		var max = activeField === "userId" ? 32 : 64;
		if (input.value.length >= max) return;
		input.value += d;
	}

	function backspace() {
		const input = getActiveInput();
		input.value = input.value.slice(0, -1);
	}

	function buildNumpad() {
		keyboardEl.innerHTML = "";

		NUM_ROWS.forEach(function (row) {
			var rowEl = document.createElement("div");
			rowEl.className = "np-row";
			row.forEach(function (d) {
				var b = document.createElement("button");
				b.type = "button";
				b.className = "np-key";
				b.textContent = d;
				b.setAttribute("aria-label", d);
				b.addEventListener("click", function () {
					appendDigit(d);
				});
				rowEl.appendChild(b);
			});
			keyboardEl.appendChild(rowEl);
		});

		var bottom = document.createElement("div");
		bottom.className = "np-row";

		var bs = document.createElement("button");
		bs.type = "button";
		bs.className = "np-key np-key--back";
		bs.innerHTML =
			'<span class="np-key-label" aria-hidden="true">⌫</span><span class="sr-only">Geri sil</span>';
		bs.setAttribute("aria-label", "Geri sil");
		bs.addEventListener("click", backspace);
		bottom.appendChild(bs);

		var z = document.createElement("button");
		z.type = "button";
		z.className = "np-key";
		z.textContent = "0";
		z.setAttribute("aria-label", "0");
		z.addEventListener("click", function () {
			appendDigit("0");
		});
		bottom.appendChild(z);

		var enter = document.createElement("button");
		enter.type = "button";
		enter.className = "np-key np-key--enter";
		enter.innerHTML =
			'<svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke-width="2.2" stroke="currentColor" width="26" height="26" aria-hidden="true"><path stroke-linecap="round" stroke-linejoin="round" d="m4.5 12.75 6 6 9-13.5" /></svg><span class="sr-only">Giriş yap</span>';
		enter.setAttribute("aria-label", "Giriş yap");
		enter.addEventListener("click", function () {
			form.requestSubmit();
		});
		bottom.appendChild(enter);

		keyboardEl.appendChild(bottom);
		setActiveField(activeField);
	}

	userIdInput.addEventListener("focus", function () {
		setActiveField("userId");
	});
	passwordInput.addEventListener("focus", function () {
		setActiveField("password");
	});

	form.addEventListener("submit", async function (e) {
		e.preventDefault();
		hideError();

		const userId = userIdInput.value.trim();
		const password = passwordInput.value;

		if (!userId || !password) {
			showError("Sicil no ve parolayı girin.");
			return;
		}

		btn.disabled = true;
		try {
			const res = await fetch("/api/auth/login", {
				method: "POST",
				headers: { "Content-Type": "application/json" },
				body: JSON.stringify({ userId, password }),
			});
			const data = await res.json().catch(() => ({}));

			if (!res.ok) {
				showError(data.error || "Giriş başarısız. Bilgilerinizi kontrol edin.");
				return;
			}

			if (data.accessToken) {
				sessionStorage.setItem("aqua_token", data.accessToken);
				sessionStorage.setItem("aqua_user", data.userId || userId);
				sessionStorage.setItem("aqua_role", data.role || "");
				sessionStorage.setItem("aqua_display_name", data.displayName || data.userId || userId);
				var areas = Array.isArray(data.saleAreaCodes) ? data.saleAreaCodes : [];
				sessionStorage.setItem("aqua_sale_areas", JSON.stringify(areas));
				sessionStorage.setItem(
					"aqua_ticket_sales",
					String(data.ticketSalesAllowed !== false && data.ticketSalesAllowed !== "false")
				);
				sessionStorage.setItem(
					"aqua_balance_load",
					String(data.balanceLoadAllowed !== false && data.balanceLoadAllowed !== "false")
				);
			}
			window.location.href = "/pos.html";
		} catch (err) {
			showError("Sunucuya bağlanılamadı. Ağınızı kontrol edin.");
		} finally {
			btn.disabled = false;
		}
	});

	buildNumpad();
})();
