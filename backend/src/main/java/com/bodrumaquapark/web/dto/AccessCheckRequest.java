package com.bodrumaquapark.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AccessCheckRequest(
		@NotBlank @Size(max = 128) String cardId,
		@NotBlank @Size(max = 64) String deviceId) {
}
