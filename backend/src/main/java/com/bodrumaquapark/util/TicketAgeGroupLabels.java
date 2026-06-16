package com.bodrumaquapark.util;

import java.util.Locale;

public final class TicketAgeGroupLabels {

	private TicketAgeGroupLabels() {
	}

	/** Raporlarda çocuk / yetişkin ayrımı için tarife adından sınıflandırma. */
	public static boolean isChildTariff(String name) {
		if (name == null || name.isBlank()) {
			return false;
		}
		String n = name.trim().toLowerCase(Locale.forLanguageTag("tr"));
		if (n.contains("çocuk")) {
			return true;
		}
		return n.contains("0–6") || n.contains("0-6") || n.contains("7–12") || n.contains("7-12");
	}

	/** Acenta (ücretsiz) tarifeleri — Kule bilet sayısına dahil edilmez. */
	public static boolean isAgencyTariff(String name) {
		if (name == null || name.isBlank()) {
			return false;
		}
		return name.trim().toLowerCase(Locale.forLanguageTag("tr")).contains("acenta");
	}
}
