package com.bodrumaquapark.web.dto;

import java.util.List;

import com.bodrumaquapark.entity.SaleArea;

public record SaleAreaAdminResponse(
		Long id,
		String code,
		String name,
		long activeProductCount,
		long totalProductCount,
		List<Long> menuPageIds,
		List<String> menuPageNames
) {

	public static SaleAreaAdminResponse from(SaleArea a, long activeProductCount, long totalProductCount) {
		List<Long> ids = a.getMenuPages().stream().map(mp -> mp.getId()).sorted().toList();
		List<String> names = a.getMenuPages().stream()
				.sorted((x, y) -> {
					int o = Integer.compare(x.getSortOrder(), y.getSortOrder());
					return o != 0 ? o : Long.compare(x.getId(), y.getId());
				})
				.map(mp -> mp.getName())
				.toList();
		return new SaleAreaAdminResponse(a.getId(), a.getCode(), a.getName(), activeProductCount, totalProductCount,
				ids, names);
	}
}
