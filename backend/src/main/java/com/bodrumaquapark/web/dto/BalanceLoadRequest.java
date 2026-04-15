package com.bodrumaquapark.web.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record BalanceLoadRequest(
		@NotNull @DecimalMin(value = "0.01", message = "Tutar en az 0,01 olmalıdır") BigDecimal amount,
		@NotBlank @Pattern(regexp = "cash|card|rate", message = "Ödeme: cash, card veya rate") String paymentMethod
) {
}
