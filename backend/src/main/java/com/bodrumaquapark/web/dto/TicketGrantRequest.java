package com.bodrumaquapark.web.dto;

import java.math.BigDecimal;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record TicketGrantRequest(
		@NotBlank @Pattern(regexp = "cash|card|credit", message = "Ödeme: cash, card veya credit") String paymentMethod,
		@NotNull @DecimalMin(value = "0.00", message = "Tutar negatif olamaz") BigDecimal amount,
		List<@Valid TicketGrantLineRequest> lines) {
}
