package com.bodrumaquapark.config;

import java.util.Arrays;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Üretimde yalnızca {@code app.access.http.cors-allowed-origins} ile sınırlı kökenlere izin verilir.
 */
@Configuration
public class CorsWebConfiguration implements WebMvcConfigurer {

	private final AccessGatewayProperties accessGatewayProperties;

	public CorsWebConfiguration(AccessGatewayProperties accessGatewayProperties) {
		this.accessGatewayProperties = accessGatewayProperties;
	}

	@Override
	public void addCorsMappings(CorsRegistry registry) {
		String raw = accessGatewayProperties.getCorsAllowedOrigins();
		if (raw == null || raw.isBlank()) {
			return;
		}
		String[] origins = Arrays.stream(raw.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toArray(String[]::new);
		if (origins.length == 0) {
			return;
		}
		registry.addMapping("/api/**")
				.allowedOrigins(origins)
				.allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
				.allowCredentials(true)
				.maxAge(3600);
	}
}
