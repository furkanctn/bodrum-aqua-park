package com.bodrumaquapark.service;

import java.math.BigDecimal;

public record SaleResult(
		BigDecimal balanceAfter,
		String productName,
		String saleAreaCode,
		String saleAreaName,
		BigDecimal amount
) {
}
