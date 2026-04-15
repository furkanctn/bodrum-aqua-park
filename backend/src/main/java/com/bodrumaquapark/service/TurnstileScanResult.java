package com.bodrumaquapark.service;

import java.math.BigDecimal;

public record TurnstileScanResult(
		boolean allowed,
		String code,
		BigDecimal balance,
		BigDecimal balanceAfter,
		BigDecimal entryFee
) {

	public static final String CODE_ALLOWED = "ALLOWED";
	public static final String CODE_INSUFFICIENT = "INSUFFICIENT_BALANCE";
	public static final String CODE_BLOCKED = "CARD_BLOCKED";
	public static final String CODE_NOT_FOUND = "CARD_NOT_FOUND";

	public static TurnstileScanResult allowed(BigDecimal balanceAfter, BigDecimal entryFeeCharged) {
		return new TurnstileScanResult(true, CODE_ALLOWED, null, balanceAfter, entryFeeCharged);
	}

	public static TurnstileScanResult insufficient(BigDecimal balance, BigDecimal entryFee) {
		return new TurnstileScanResult(false, CODE_INSUFFICIENT, balance, balance, entryFee);
	}

	public static TurnstileScanResult blocked() {
		return new TurnstileScanResult(false, CODE_BLOCKED, null, null, null);
	}

	public static TurnstileScanResult notFound() {
		return new TurnstileScanResult(false, CODE_NOT_FOUND, null, null, null);
	}
}
