package com.bodrumaquapark.web.dto;

import java.math.BigDecimal;

public record SaleAreaRevenueDto(
		String saleAreaCode,
		String saleAreaName,
		long saleLineCount,
		BigDecimal revenueTry
) {
}
