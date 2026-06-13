package com.bodrumaquapark.web.dto;

import java.math.BigDecimal;

import com.bodrumaquapark.entity.Product;

public record ProductResponse(
		Long id,
		String name,
		BigDecimal price,
		String saleAreaCode,
		String saleAreaName,
		Long menuPageId,
		String menuPageCode,
		String menuPageName,
		Integer stockQuantity,
		boolean active
) {

	public static ProductResponse from(Product p) {
		return from(p, null, null);
	}

	public static ProductResponse from(Product p, String saleAreaCode, String saleAreaName) {
		Long mpId = null;
		String mpCode = null;
		String mpName = null;
		if (p.getMenuPage() != null) {
			mpId = p.getMenuPage().getId();
			mpCode = p.getMenuPage().getCode();
			mpName = p.getMenuPage().getName();
		}
		return new ProductResponse(p.getId(), p.getName(), p.getPrice(), saleAreaCode, saleAreaName, mpId, mpCode,
				mpName, p.getStockQuantity(), p.isActive());
	}
}
