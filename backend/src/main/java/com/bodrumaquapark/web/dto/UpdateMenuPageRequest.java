package com.bodrumaquapark.web.dto;

import jakarta.validation.constraints.Size;

public record UpdateMenuPageRequest(
		@Size(max = 255) String name,
		Integer sortOrder
) {
}
