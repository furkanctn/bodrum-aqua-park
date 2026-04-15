package com.bodrumaquapark.web.dto;

import com.bodrumaquapark.entity.SaleArea;

public record SaleAreaResponse(
		Long id,
		String code,
		String name
) {

	public static SaleAreaResponse from(SaleArea a) {
		return new SaleAreaResponse(a.getId(), a.getCode(), a.getName());
	}
}
