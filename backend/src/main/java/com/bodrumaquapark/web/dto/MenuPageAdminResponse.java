package com.bodrumaquapark.web.dto;

import com.bodrumaquapark.entity.MenuPage;

public record MenuPageAdminResponse(
		Long id,
		String code,
		String name,
		int sortOrder,
		String saleAreaCode,
		String saleAreaName,
		long productCount
) {

	public static MenuPageAdminResponse from(MenuPage m, long productCount) {
		return new MenuPageAdminResponse(
				m.getId(),
				m.getCode(),
				m.getName(),
				m.getSortOrder(),
				m.getSaleArea().getCode(),
				m.getSaleArea().getName(),
				productCount);
	}
}
