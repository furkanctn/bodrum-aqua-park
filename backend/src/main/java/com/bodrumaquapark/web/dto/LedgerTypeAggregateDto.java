package com.bodrumaquapark.web.dto;

import java.math.BigDecimal;

import com.bodrumaquapark.entity.TransactionType;

public record LedgerTypeAggregateDto(
		TransactionType type,
		long lineCount,
		BigDecimal amountChangeSum
) {
}
