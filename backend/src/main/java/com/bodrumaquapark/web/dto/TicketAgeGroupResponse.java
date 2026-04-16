package com.bodrumaquapark.web.dto;

import java.math.BigDecimal;

import com.bodrumaquapark.entity.TicketAgeGroup;

public record TicketAgeGroupResponse(Long id, String name, BigDecimal price, int sortOrder, boolean active) {

	public static TicketAgeGroupResponse from(TicketAgeGroup e) {
		return new TicketAgeGroupResponse(e.getId(), e.getName(), e.getPrice(), e.getSortOrder(), e.isActive());
	}
}
