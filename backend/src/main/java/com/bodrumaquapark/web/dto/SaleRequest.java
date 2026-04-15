package com.bodrumaquapark.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record SaleRequest(
		@NotBlank String cardUid,
		@NotNull Long productId
) {
}
