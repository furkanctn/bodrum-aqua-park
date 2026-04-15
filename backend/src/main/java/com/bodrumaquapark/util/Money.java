package com.bodrumaquapark.util;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

public final class Money {

	private Money() {
	}

	/** Defter / API metinleri için TRY (tr-TR) */
	public static String formatTryLabel(BigDecimal value) {
		DecimalFormat df = new DecimalFormat("#,##0.00", DecimalFormatSymbols.getInstance(Locale.forLanguageTag("tr-TR")));
		return df.format(normalize(value)) + " \u20BA";
	}

	public static BigDecimal normalize(BigDecimal amount) {
		if (amount == null) {
			return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
		}
		return amount.setScale(2, RoundingMode.HALF_UP);
	}
}
