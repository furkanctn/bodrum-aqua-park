package com.bodrumaquapark.util;

import java.util.Locale;
import java.util.Optional;

/**
 * Kart kimliği normalizasyonu — {@link MifareUid} kanonik formuna yönlendirir.
 */
public final class RfidCardIds {

	private RfidCardIds() {
	}

	public static String normalize(String raw) {
		return normalize(raw, true, true);
	}

	public static String normalize(String raw, boolean legacyDecimalLookup, boolean includeReversedBytes) {
		Optional<MifareUid.Parsed> parsed = MifareUid.parse(raw, legacyDecimalLookup, includeReversedBytes);
		if (parsed.isPresent()) {
			return parsed.get().canonical();
		}
		if (raw == null) {
			return "";
		}
		String trimmed = raw.trim().replaceAll("\\s+", "");
		if (trimmed.isEmpty()) {
			return "";
		}
		return trimmed.toUpperCase(Locale.ROOT);
	}

	public static Optional<MifareUid.Parsed> parse(String raw, boolean legacyDecimalLookup, boolean includeReversedBytes) {
		return MifareUid.parse(raw, legacyDecimalLookup, includeReversedBytes);
	}
}
