package com.bodrumaquapark.web.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record TicketGrantRequest(
		@NotBlank @Pattern(regexp = "cash|card|credit", message = "Ödeme: cash, card veya credit") String paymentMethod,
		@NotNull @DecimalMin(value = "0.01", message = "Tutar en az 0,01 olmalıdır") BigDecimal amount) {
}
