package com.bodrumaquapark.web.dto;

import com.bodrumaquapark.entity.SaleArea;

public record SaleAreaAdminResponse(
		Long id,
		String code,
		String name,
		long activeProductCount,
		long totalProductCount
) {

	public static SaleAreaAdminResponse from(SaleArea a, long activeProductCount, long totalProductCount) {
		return new SaleAreaAdminResponse(a.getId(), a.getCode(), a.getName(), activeProductCount, totalProductCount);
	}
}
