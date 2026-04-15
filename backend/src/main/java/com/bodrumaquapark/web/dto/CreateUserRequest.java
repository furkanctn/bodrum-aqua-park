package com.bodrumaquapark.web.dto;

import java.util.List;

import com.bodrumaquapark.entity.RoleCode;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateUserRequest(
		@NotBlank @Size(max = 64) String userId,
		@NotBlank @Size(min = 4, max = 128) String password,
		@Size(max = 120) String displayName,
		@NotNull RoleCode role,
		List<String> saleAreaCodes,
		Boolean ticketSalesAllowed,
		Boolean balanceLoadAllowed
) {
}
