package com.bodrumaquapark.web.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record AdminSummaryReportDto(
		LocalDate fromInclusive,
		LocalDate toInclusive,
		String timeZone,
		List<LedgerTypeAggregateDto> byTransactionType,
		BigDecimal productSaleRevenueTotal,
		long productSaleLineCount
) {
}
