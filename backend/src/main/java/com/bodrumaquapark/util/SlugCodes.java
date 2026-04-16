package com.bodrumaquapark.util;

import java.text.Normalizer;
import java.util.Locale;
import java.util.function.Predicate;

/**
 * Görünen adlardan veritabanı kodu üretir (satış alanı / menü sayfası).
 */
public final class SlugCodes {

	private SlugCodes() {
	}

	public static String slugFromDisplayName(String raw) {
		if (raw == null || raw.isBlank()) {
			return "ITEM";
		}
		String s = Normalizer.normalize(raw.trim(), Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
		s = s.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]+", "_");
		s = s.replaceAll("^_+", "").replaceAll("_+$", "").replaceAll("_+", "_");
		if (s.isEmpty()) {
			s = "ITEM";
		}
		if (s.length() > 60) {
			s = s.substring(0, 60).replaceAll("_+$", "");
			if (s.isEmpty()) {
				s = "ITEM";
			}
		}
		return s;
	}

	public static String uniqueCode(String base, Predicate<String> exists) {
		String b = base;
		if (!exists.test(b)) {
			return b;
		}
		for (int n = 2; n < 10_000; n++) {
			String suffix = "_" + n;
			int max = Math.max(1, 64 - suffix.length());
			String truncated = b.length() > max ? b.substring(0, max) : b;
			String cand = truncated + suffix;
			if (!exists.test(cand)) {
				return cand;
			}
		}
		throw new IllegalStateException("Benzersiz kod üretilemedi");
	}
}
