package com.bodrumaquapark.web.dto;

import java.math.BigDecimal;
import java.time.Instant;

import com.bodrumaquapark.entity.CardLedgerEntry;
import com.bodrumaquapark.entity.Product;

public record LedgerEntryResponse(
		Long id,
		String type,
		BigDecimal amountChange,
		BigDecimal balanceAfter,
		String description,
		Instant createdAt,
		String productName,
		String saleAreaName
) {

	public static LedgerEntryResponse from(CardLedgerEntry e) {
		String productName = null;
		String saleAreaName = null;
		Product p = e.getProduct();
		if (p != null) {
			productName = p.getName();
			if (p.getSaleArea() != null) {
				saleAreaName = p.getSaleArea().getName();
			}
		}
		return new LedgerEntryResponse(
				e.getId(),
				e.getType().name(),
				e.getAmountChange(),
				e.getBalanceAfter(),
				e.getDescription(),
				e.getCreatedAt(),
				productName,
				saleAreaName);
	}
}
