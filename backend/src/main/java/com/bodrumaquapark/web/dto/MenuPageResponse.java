package com.bodrumaquapark.web.dto;

import com.bodrumaquapark.entity.MenuPage;
import com.bodrumaquapark.entity.SaleArea;

public record MenuPageResponse(
		Long id,
		String code,
		String name,
		int sortOrder,
		String saleAreaCode,
		String saleAreaName
) {

	public static MenuPageResponse from(MenuPage m, SaleArea area) {
		return new MenuPageResponse(
				m.getId(),
				m.getCode(),
				m.getName(),
				m.getSortOrder(),
				area.getCode(),
				area.getName());
	}
}
