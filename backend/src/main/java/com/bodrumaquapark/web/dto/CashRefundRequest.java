package com.bodrumaquapark.web.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

public record CashRefundRequest(
		@NotNull
		@DecimalMin(value = "0.00", inclusive = true)
		BigDecimal amount) {
}

