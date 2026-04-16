package com.bodrumaquapark.web.dto;

import java.math.BigDecimal;
import java.time.Instant;

import com.bodrumaquapark.entity.TransactionType;

public record AdminDayLedgerLineDto(
		Instant createdAt,
		TransactionType type,
		BigDecimal amountChange,
		BigDecimal balanceAfter,
		String productName,
		String saleAreaName,
		String description,
		String cardUidMasked
) {
}
