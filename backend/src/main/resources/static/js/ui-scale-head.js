/**
 * Ortak ölçek başlatma — tüm sayfalar bu dosyayı (pos-terminal-scale.css ile birlikte) kullanır.
 * localStorage: aqua_pos_ui_scale | session: aqua_pos_perf (posPerf=1)
 * aqua_pos_ui_scale_rev: migrasyon sürümü (2 = %105 artefact → %124 vb.)
 */
(function () {
	var OLD_DEFAULT = 0.56;
	var STALE_105 = 1.05; /* eski oturumlarda kalan yanlış varsayılan */
	var NEW_DEFAULT = 1.24;
	try {
		if (window.location.search.indexOf("posPerf=1") >= 0) {
			sessionStorage.setItem("aqua_pos_perf", "1");
		} else {
			sessionStorage.removeItem("aqua_pos_perf");
		}
		if (sessionStorage.getItem("aqua_pos_perf") === "1") {
			document.documentElement.classList.add("pos-perf");
		} else {
			var rev = localStorage.getItem("aqua_pos_ui_scale_rev");
			var saved = localStorage.getItem("aqua_pos_ui_scale");
			if (rev !== "2") {
				var z0 = saved != null && saved !== "" ? parseFloat(saved) : NaN;
				var useNew =
					saved == null ||
					saved === "" ||
					(!isNaN(z0) && Math.abs(z0 - OLD_DEFAULT) < 1e-5) ||
					(!isNaN(z0) && Math.abs(z0 - STALE_105) < 1e-5);
				if (useNew) {
					localStorage.setItem("aqua_pos_ui_scale", String(NEW_DEFAULT));
					document.documentElement.style.setProperty("--pos-terminal-ui-scale", String(NEW_DEFAULT));
				} else if (!isNaN(z0) && z0 >= 0.4 && z0 <= 1.5) {
					document.documentElement.style.setProperty("--pos-terminal-ui-scale", String(z0));
				}
				localStorage.setItem("aqua_pos_ui_scale_rev", "2");
			} else if (saved != null && saved !== "") {
				var z = parseFloat(saved);
				if (!isNaN(z) && z >= 0.4 && z <= 1.5) {
					document.documentElement.style.setProperty("--pos-terminal-ui-scale", String(z));
				}
			}
		}
	} catch (e) {}
})();
