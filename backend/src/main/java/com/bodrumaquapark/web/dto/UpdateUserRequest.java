package com.bodrumaquapark.web.dto;

import java.util.List;

import com.bodrumaquapark.entity.RoleCode;

import jakarta.validation.constraints.Size;

public record UpdateUserRequest(
		@Size(min = 4, max = 128) String password,
		@Size(max = 120) String displayName,
		Boolean active,
		RoleCode role,
		List<String> saleAreaCodes,
		Boolean ticketSalesAllowed,
		Boolean balanceLoadAllowed
) {
}
