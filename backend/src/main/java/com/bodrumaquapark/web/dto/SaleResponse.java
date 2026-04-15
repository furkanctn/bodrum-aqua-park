package com.bodrumaquapark.web.dto;

import java.math.BigDecimal;

import com.bodrumaquapark.service.SaleResult;

public record SaleResponse(
		BigDecimal balanceAfter,
		String productName,
		String saleAreaCode,
		String saleAreaName,
		BigDecimal amount
) {

	public static SaleResponse from(SaleResult r) {
		return new SaleResponse(r.balanceAfter(), r.productName(), r.saleAreaCode(), r.saleAreaName(), r.amount());
	}
}
