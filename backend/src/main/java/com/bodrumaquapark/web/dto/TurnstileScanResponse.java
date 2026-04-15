package com.bodrumaquapark.web.dto;

import java.math.BigDecimal;

import com.bodrumaquapark.service.TurnstileScanResult;

public record TurnstileScanResponse(
		boolean allowed,
		String code,
		BigDecimal balance,
		BigDecimal balanceAfter,
		BigDecimal entryFee
) {

	public static TurnstileScanResponse from(TurnstileScanResult r) {
		return new TurnstileScanResponse(r.allowed(), r.code(), r.balance(), r.balanceAfter(), r.entryFee());
	}
}
