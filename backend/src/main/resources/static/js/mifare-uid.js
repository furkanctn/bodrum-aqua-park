/**
 * Mifare UID normalizasyonu — backend MifareUid.java ile uyumlu (istemci tarafı).
 */
(function (global) {
	var HEX_SINGLE = 8;
	var HEX_DOUBLE = 14;

	function strip(raw) {
		var t = String(raw || "")
			.trim()
			.replace(/[\s:\-_.]+/g, "");
		if (/^0x/i.test(t)) {
			t = t.slice(2);
		}
		return t.toUpperCase();
	}

	function isHex(s) {
		return s.length >= 2 && /^[0-9A-F]+$/.test(s);
	}

	function stripLeadingZeros(hex) {
		var h = hex.toUpperCase();
		var i = 0;
		while (i < h.length - 1 && h.charAt(i) === "0") {
			i++;
		}
		return h.slice(i);
	}

	function hexToBytes(hex) {
		var h = stripLeadingZeros(hex);
		var byteLen = Math.ceil(h.length / 2);
		if (byteLen < 4) {
			byteLen = 4;
		} else if (byteLen > 4 && byteLen < 7) {
			byteLen = 7;
		} else if (byteLen > 7) {
			return null;
		}
		var padded = h.length % 2 === 1 ? "0" + h : h;
		while (padded.length < byteLen * 2) {
			padded = "0" + padded;
		}
		if (padded.length > byteLen * 2) {
			padded = padded.slice(padded.length - byteLen * 2);
		}
		var out = [];
		for (var i = 0; i < byteLen; i++) {
			out.push(parseInt(padded.slice(i * 2, i * 2 + 2), 16));
		}
		return out;
	}

	function decimalToBytes(decStr) {
		try {
			var bi = BigInt(decStr);
			if (bi < 0n) {
				return null;
			}
			var hex = bi.toString(16).toUpperCase();
			return hexToBytes(hex);
		} catch (e) {
			return null;
		}
	}

	function toPaddedHex(bytes) {
		var s = "";
		for (var i = 0; i < bytes.length; i++) {
			s += bytes[i].toString(16).padStart(2, "0").toUpperCase();
		}
		return s;
	}

	function hasHexLetter(s) {
		return /[A-F]/.test(s);
	}

	function parse(raw, legacyDecimal) {
		var stripped = strip(raw);
		if (!stripped.length) {
			return null;
		}
		var bytes = null;
		var allDigits = /^[0-9]+$/.test(stripped);
		if (legacyDecimal !== false && allDigits) {
			bytes = decimalToBytes(stripped);
		}
		if (!bytes && isHex(stripped) && (hasHexLetter(stripped) || !allDigits)) {
			bytes = hexToBytes(stripped);
		}
		if (!bytes && isHex(stripped) && allDigits) {
			bytes = hexToBytes(stripped);
		}
		if (!bytes || (bytes.length !== 4 && bytes.length !== 7)) {
			return null;
		}
		return toPaddedHex(bytes);
	}

	function cleanUid(s) {
		var stripped = strip(s);
		if (!stripped.length) {
			return "";
		}
		var canonical = parse(stripped, true);
		return canonical || stripped;
	}

	function isPlausible(s) {
		return parse(s, true) !== null;
	}

	function minUidLength() {
		return HEX_SINGLE;
	}

	function maxUidLength() {
		return HEX_DOUBLE;
	}

	global.MifareUidUtil = {
		cleanUid: cleanUid,
		parse: parse,
		isPlausible: isPlausible,
		minUidLength: minUidLength,
		maxUidLength: maxUidLength,
		HEX_SINGLE: HEX_SINGLE,
		HEX_DOUBLE: HEX_DOUBLE,
	};
})(typeof window !== "undefined" ? window : globalThis);
