package com.bodrumaquapark.util;

import java.util.Locale;

/**
 * HID / klavye modu kart okuyuculardan gelen kimlikleri tek forma getirir.
 */
public final class RfidCardIds {

	private RfidCardIds() {
	}

	public static String normalize(String raw) {
		if (raw == null) {
			return "";
		}
		String trimmed = raw.trim().replaceAll("\\s+", "");
		if (trimmed.isEmpty()) {
			return "";
		}
		return trimmed.toUpperCase(Locale.ROOT);
	}
}
