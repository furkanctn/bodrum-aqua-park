package com.bodrumaquapark.web.dto;

import java.math.BigDecimal;
import java.util.List;

public record SaleAreaCashRegisterSectionDto(
		String saleAreaCode,
		String saleAreaName,
		long saleLineCount,
		BigDecimal revenueTry,
		List<ProductRevenueDto> products) {
}
