/**
 * Ortak ölçek başlatma — tüm sayfalar bu dosyayı (pos-terminal-scale.css ile birlikte) kullanır.
 * localStorage: aqua_pos_ui_scale | session: aqua_pos_perf (posPerf=1), aqua_login_lite_ui (liteUi=1)
 * aqua_pos_ui_scale_rev: migrasyon sürümü (3 = varsayılan %110)
 */
(function () {
	var OLD_DEFAULT = 0.56;
	var STALE_105 = 1.05; /* eski oturumlarda kalan yanlış varsayılan */
	var STALE_124 = 1.24; /* önceki varsayılan */
	var NEW_DEFAULT = 1.1;
	try {
		if (window.location.search.indexOf("posPerf=1") >= 0) {
			sessionStorage.setItem("aqua_pos_perf", "1");
		} else {
			sessionStorage.removeItem("aqua_pos_perf");
		}
		if (window.location.search.indexOf("liteUi=1") >= 0) {
			sessionStorage.setItem("aqua_login_lite_ui", "1");
		}
		if (sessionStorage.getItem("aqua_pos_perf") === "1") {
			document.documentElement.classList.add("pos-perf");
		} else {
			var rev = localStorage.getItem("aqua_pos_ui_scale_rev");
			var saved = localStorage.getItem("aqua_pos_ui_scale");
			if (rev !== "3") {
				var z0 = saved != null && saved !== "" ? parseFloat(saved) : NaN;
				var useNew =
					saved == null ||
					saved === "" ||
					(!isNaN(z0) && Math.abs(z0 - OLD_DEFAULT) < 1e-5) ||
					(!isNaN(z0) && Math.abs(z0 - STALE_105) < 1e-5) ||
					(!isNaN(z0) && Math.abs(z0 - STALE_124) < 1e-5);
				if (useNew) {
					localStorage.setItem("aqua_pos_ui_scale", String(NEW_DEFAULT));
					document.documentElement.style.setProperty("--pos-terminal-ui-scale", String(NEW_DEFAULT));
				} else if (!isNaN(z0) && z0 >= 0.4 && z0 <= 1.5) {
					document.documentElement.style.setProperty("--pos-terminal-ui-scale", String(z0));
				}
				localStorage.setItem("aqua_pos_ui_scale_rev", "3");
			} else if (saved != null && saved !== "") {
				var z = parseFloat(saved);
				if (!isNaN(z) && z >= 0.4 && z <= 1.5) {
					document.documentElement.style.setProperty("--pos-terminal-ui-scale", String(z));
				}
			}
		}
	} catch (e) {}
	try {
		var ua = typeof navigator !== "undefined" ? String(navigator.userAgent || "") : "";
		var isLogin =
			document.documentElement.classList.contains("login-html") ||
			(typeof location !== "undefined" &&
				(location.pathname === "/" || /index\.html$/i.test(location.pathname || "")));
		/** Gömülü WebView / eski motor: girişte blur, mask ve backdrop’tan kaçın */
		function loginNeedsLiteUi(u) {
			if (!u) return false;
			if (/JavaFX/i.test(u)) return true;
			if (/;\s*wv\)/i.test(u) || /\bwv\b/i.test(u)) return true;
			if (/WebView/i.test(u)) return true;
			if (/FBAN|FBAV/i.test(u)) return true;
			return false;
		}
		var forceLite =
			typeof window !== "undefined" &&
			window.location.search.indexOf("liteUi=1") >= 0;
		try {
			if (!forceLite && sessionStorage.getItem("aqua_login_lite_ui") === "1") {
				forceLite = true;
			}
		} catch (sf) {}
		if (isLogin && (loginNeedsLiteUi(ua) || forceLite)) {
			document.documentElement.classList.add("login-javafx-lite", "login-lite-ui");
			try {
				if (forceLite) sessionStorage.setItem("aqua_login_lite_ui", "1");
			} catch (sf2) {}
		} else if (/JavaFX/i.test(ua)) {
			document.documentElement.classList.add("login-javafx-lite");
		}
	} catch (e2) {}
	/* Yerel geliştirme: Spring DevTools LiveReload (localhost:35729) */
	try {
		var lh = typeof location !== "undefined" ? location.hostname : "";
		if (lh === "localhost" || lh === "127.0.0.1" || lh === "::1" || lh === "[::1]") {
			var lrHost = lh === "::1" || lh === "[::1]" ? "localhost" : lh;
			var lr = document.createElement("script");
			lr.src = "http://" + lrHost + ":35729/livereload.js?snipver=1";
			document.head.appendChild(lr);
		}
	} catch (e3) {}
})();
