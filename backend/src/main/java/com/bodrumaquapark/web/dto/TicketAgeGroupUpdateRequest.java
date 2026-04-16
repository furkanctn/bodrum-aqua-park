package com.bodrumaquapark.web.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record TicketAgeGroupUpdateRequest(
		@NotBlank @Size(max = 160) String name,
		@NotNull @DecimalMin(value = "0.0", inclusive = true) BigDecimal price,
		@NotNull Integer sortOrder,
		@NotNull Boolean active) {
}
