package com.bodrumaquapark.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Menü sayfası kodu istekte yer almaz; sunucu görünen addan benzersiz kod üretir.
 */
public record CreateMenuPageRequest(
		@NotBlank @Size(max = 64) String saleAreaCode,
		@NotBlank @Size(max = 255) String name,
		Integer sortOrder
) {
}
