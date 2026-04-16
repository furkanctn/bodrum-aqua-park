package com.bodrumaquapark.web.dto;

import java.math.BigDecimal;

public record ProductRevenueDto(
		long productId,
		String productName,
		long saleLineCount,
		BigDecimal revenueTry
) {
}
