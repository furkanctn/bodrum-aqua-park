package com.bodrumaquapark.web.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateProductRequest(
		@NotNull Long menuPageId,
		@NotBlank @Size(max = 255) String name,
		@NotNull BigDecimal price,
		Integer stockQuantity
) {
}
