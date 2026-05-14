package com.bodrumaquapark.web.dto;

import com.bodrumaquapark.entity.PassType;

import jakarta.validation.constraints.NotBlank;

public record AssignPassRequest(
		@NotBlank String cardId,
		PassType passType) {
}
