/**
 * Çözünürlük önizleme — POS bank (ör. 1024×768) için sayfa gövdesini sabit piksel boyutuna sıkıştırır.
 * Kalıcı seçim: localStorage "aqua_viewport_test" (örn. "1024x768" veya boş).
 */
(function () {
	var STORAGE_KEY = "aqua_viewport_test";

	var PRESETS = [
		{ label: "Tam pencere (ölçü yok)", value: "" },
		{ label: "1024 × 768 (POS tipik)", value: "1024x768" },
		{ label: "1280 × 800", value: "1280x800" },
		{ label: "1280 × 1024", value: "1280x1024" },
		{ label: "1366 × 768", value: "1366x768" },
		{ label: "800 × 600", value: "800x600" },
	];

	function parsePreset(v) {
		if (!v || typeof v !== "string") {
			return null;
		}
		var m = /^(\d+)\s*x\s*(\d+)$/i.exec(v.trim());
		if (!m) {
			return null;
		}
		var w = parseInt(m[1], 10);
		var h = parseInt(m[2], 10);
		if (w < 320 || h < 240 || w > 4096 || h > 4096) {
			return null;
		}
		return { w: w, h: h };
	}

	function applyPreset(value) {
		var root = document.documentElement;
		var parsed = parsePreset(value);
		if (!parsed) {
			root.classList.remove("viewport-test-on");
			root.style.removeProperty("--viewport-test-w");
			root.style.removeProperty("--viewport-test-h");
			try {
				if (value === "") {
					localStorage.removeItem(STORAGE_KEY);
				}
			} catch (e) {}
			return;
		}
		root.classList.add("viewport-test-on");
		root.style.setProperty("--viewport-test-w", parsed.w + "px");
		root.style.setProperty("--viewport-test-h", parsed.h + "px");
		try {
			localStorage.setItem(STORAGE_KEY, parsed.w + "x" + parsed.h);
		} catch (e) {}
	}

	function initBar() {
		if (document.getElementById("viewport-test-bar")) {
			return;
		}
		var bar = document.createElement("div");
		bar.id = "viewport-test-bar";
		bar.setAttribute("role", "region");
		bar.setAttribute("aria-label", "Test çözünürlüğü");

		var lab = document.createElement("label");
		lab.htmlFor = "viewport-test-select";
		lab.appendChild(document.createTextNode("Çözünürlük"));

		var sel = document.createElement("select");
		sel.id = "viewport-test-select";
		PRESETS.forEach(function (p) {
			var opt = document.createElement("option");
			opt.value = p.value;
			opt.textContent = p.label;
			sel.appendChild(opt);
		});

		var saved = "";
		try {
			saved = localStorage.getItem(STORAGE_KEY) || "";
		} catch (e) {}
		var match = PRESETS.some(function (p) {
			return p.value === saved;
		});
		sel.value = match ? saved : "";

		sel.addEventListener("change", function () {
			applyPreset(sel.value);
		});

		lab.appendChild(sel);
		bar.appendChild(lab);
		document.body.appendChild(bar);

		applyPreset(sel.value);
	}

	if (document.readyState === "loading") {
		document.addEventListener("DOMContentLoaded", initBar);
	} else {
		initBar();
	}
})();
