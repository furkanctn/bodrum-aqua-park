package com.bodrumaquapark.web.dto;

import java.math.BigDecimal;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record BekoPosSendBasketRequest(
		@Size(max = 32) String context,
		@NotEmpty @Valid List<Line> items,
		/** cash | card | rate — boşsa POS cihazında ödeme tipi seçilir */
		@Size(max = 16) String paymentMethod,
		Integer discountPercent,
		@Size(max = 120) String note) {

	public record Line(
			@NotNull @Size(max = 80) String name,
			@NotNull BigDecimal unitPrice,
			Integer quantity) {
	}
}
