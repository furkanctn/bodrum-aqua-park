package com.bodrumaquapark.web.dto;

import java.math.BigDecimal;

public record CashRefundResponse(
		String uid,
		BigDecimal balance,
		BigDecimal refundedCash,
		BigDecimal forfeitedBalance) {
}
