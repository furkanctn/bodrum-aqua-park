package com.bodrumaquapark.web.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;

public record UpdateProductRequest(
		@Size(max = 255) String name,
		@DecimalMin(value = "0.0", inclusive = true) BigDecimal price,
		Integer stockQuantity,
		Boolean active,
		@Size(max = 64) String saleAreaCode
) {
}
