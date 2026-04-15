package com.bodrumaquapark.web.dto;

import java.math.BigDecimal;

import com.bodrumaquapark.entity.Product;

public record ProductResponse(
		Long id,
		String name,
		BigDecimal price,
		String saleAreaCode,
		String saleAreaName,
		Integer stockQuantity,
		boolean active
) {

	public static ProductResponse from(Product p) {
		return new ProductResponse(p.getId(), p.getName(), p.getPrice(), p.getSaleArea().getCode(),
				p.getSaleArea().getName(), p.getStockQuantity(), p.isActive());
	}
}
