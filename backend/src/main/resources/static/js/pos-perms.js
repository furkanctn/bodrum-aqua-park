(function (global) {
	function readBool(key, def) {
		var v = sessionStorage.getItem(key);
		if (v === null || v === "") return def;
		return v === "true" || v === "1";
	}

	function readJwtPayload(token) {
		if (!token || typeof token !== "string") return null;
		var parts = token.split(".");
		if (parts.length < 2) return null;
		try {
			var b64 = parts[1].replace(/-/g, "+").replace(/_/g, "/");
			while (b64.length % 4) b64 += "=";
			return JSON.parse(atob(b64));
		} catch (e) {
			return null;
		}
	}

	function readJwtBooleanClaim(token, key, defaultValue) {
		var payload = readJwtPayload(token);
		if (!payload || payload[key] == null) return defaultValue;
		var v = payload[key];
		if (typeof v === "boolean") return v;
		if (v === "true" || v === "1") return true;
		if (v === "false" || v === "0") return false;
		return defaultValue;
	}

	function readJwtAreas(token) {
		var payload = readJwtPayload(token);
		if (!payload || payload.areas == null) return null;
		var raw = payload.areas;
		if (typeof raw !== "string" || !raw.trim()) return [];
		return raw
			.split(",")
			.map(function (s) {
				return String(s || "").trim();
			})
			.filter(Boolean);
	}

	function parseSessionAreas() {
		try {
			var raw = JSON.parse(sessionStorage.getItem("aqua_sale_areas") || "[]");
			if (!Array.isArray(raw)) return [];
			return raw
				.map(function (c) {
					return String(c || "").trim();
				})
				.filter(Boolean);
		} catch (e) {
			return [];
		}
	}

	function kartNavLabel(ticketSales, areas, namesByCode) {
		if (ticketSales) return "Kart satış";
		if (areas.length === 1) {
			var code = areas[0];
			return (namesByCode && namesByCode[code]) || code;
		}
		if (areas.length > 1) return "Ürün satış";
		return "Satış";
	}

	function permissionFlagsFromSession() {
		var token = sessionStorage.getItem("aqua_token") || "";
		var ticketSales = readJwtBooleanClaim(token, "ticket", readBool("aqua_ticket_sales", false));
		var balanceLoad = readJwtBooleanClaim(token, "balance", readBool("aqua_balance_load", true));
		var sessionAreas = parseSessionAreas();
		var jwtAreas = readJwtAreas(token);
		/** Oturum (login + /api/auth/me) JWT'den daha güncel olabilir */
		var saleAreas = sessionAreas.length
			? sessionAreas
			: jwtAreas != null && jwtAreas.length
				? jwtAreas
				: [];
		return { ticketSales: ticketSales, balanceLoad: balanceLoad, saleAreas: saleAreas };
	}

	function persistPermissionFlags(flags) {
		if (!flags) return;
		sessionStorage.setItem("aqua_ticket_sales", flags.ticketSales ? "true" : "false");
		sessionStorage.setItem("aqua_balance_load", flags.balanceLoad ? "true" : "false");
		sessionStorage.setItem("aqua_sale_areas", JSON.stringify(flags.saleAreas || []));
	}

	function syncNavKartLabel(namesByCode, flagsOverride) {
		var flags = flagsOverride || permissionFlagsFromSession();
		var label = kartNavLabel(flags.ticketSales, flags.saleAreas, namesByCode);
		var el = document.getElementById("nav-kart-label");
		var nav = document.getElementById("nav-kart");
		if (el) el.textContent = label;
		if (nav) {
			nav.setAttribute("title", label);
			nav.hidden = !(flags.ticketSales || flags.saleAreas.length > 0);
		}
		return flags;
	}

	global.AquaPosPerms = {
		kartNavLabel: kartNavLabel,
		permissionFlagsFromSession: permissionFlagsFromSession,
		persistPermissionFlags: persistPermissionFlags,
		syncNavKartLabel: syncNavKartLabel,
	};

	if (document.readyState === "loading") {
		document.addEventListener("DOMContentLoaded", function () {
			syncNavKartLabel();
		});
	} else {
		syncNavKartLabel();
	}
})(window);
