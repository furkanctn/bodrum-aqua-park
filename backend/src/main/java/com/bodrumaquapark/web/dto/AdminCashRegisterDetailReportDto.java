package com.bodrumaquapark.web.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record AdminCashRegisterDetailReportDto(
		LocalDate fromInclusive,
		LocalDate toInclusive,
		String timeZone,
		BigDecimal totalRevenueTry,
		long totalSaleLineCount,
		List<SaleAreaCashRegisterSectionDto> sections) {
}
