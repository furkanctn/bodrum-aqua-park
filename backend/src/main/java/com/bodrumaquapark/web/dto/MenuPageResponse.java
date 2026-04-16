package com.bodrumaquapark.web.dto;

import com.bodrumaquapark.entity.MenuPage;

public record MenuPageResponse(
		Long id,
		String code,
		String name,
		int sortOrder,
		String saleAreaCode,
		String saleAreaName
) {

	public static MenuPageResponse from(MenuPage m) {
		return new MenuPageResponse(
				m.getId(),
				m.getCode(),
				m.getName(),
				m.getSortOrder(),
				m.getSaleArea().getCode(),
				m.getSaleArea().getName());
	}
}
