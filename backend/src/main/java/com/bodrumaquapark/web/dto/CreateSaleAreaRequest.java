package com.bodrumaquapark.web.dto;

import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateSaleAreaRequest(
		/** Boşsa addan otomatik üretilir */
		@Size(max = 64) String code,
		@NotBlank @Size(max = 255) String name,
		List<Long> menuPageIds
) {
}
