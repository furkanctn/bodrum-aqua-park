package com.bodrumaquapark.web.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateProductRequest(
		@NotBlank @Size(max = 64) String saleAreaCode,
		@NotBlank @Size(max = 255) String name,
		@NotNull @DecimalMin(value = "0.0", inclusive = true) BigDecimal price,
		Integer stockQuantity
) {
}
