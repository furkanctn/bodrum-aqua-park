package com.bodrumaquapark.web.dto;

import jakarta.validation.constraints.NotBlank;

public record TurnstileScanRequest(
		@NotBlank String cardUid
) {
}
