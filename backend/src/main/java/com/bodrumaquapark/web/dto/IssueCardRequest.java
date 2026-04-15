package com.bodrumaquapark.web.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record IssueCardRequest(
		@NotBlank String uid,
		@NotNull BigDecimal initialBalance
) {
}
