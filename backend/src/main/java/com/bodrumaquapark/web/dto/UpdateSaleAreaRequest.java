package com.bodrumaquapark.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateSaleAreaRequest(
		@NotBlank @Size(max = 255) String name
) {
}
