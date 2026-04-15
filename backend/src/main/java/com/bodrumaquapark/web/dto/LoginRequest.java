package com.bodrumaquapark.web.dto;

import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
		@NotBlank(message = "Kullanıcı ID zorunlu") String userId,
		@NotBlank(message = "Şifre zorunlu") String password
) {
}
