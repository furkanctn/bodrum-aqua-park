package com.bodrumaquapark.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.jwt")
public class JwtProperties {

	/** HS256 için en az 32 bayt; üretimde ortam değişkeni kullanın */
	private String secret = "bodrum-aqua-park-dev-secret-change-in-prod-32b";

	/** Oturum süresi (saat) */
	private long expirationHours = 12;

	public String getSecret() {
		return secret;
	}

	public void setSecret(String secret) {
		this.secret = secret;
	}

	public long getExpirationHours() {
		return expirationHours;
	}

	public void setExpirationHours(long expirationHours) {
		this.expirationHours = expirationHours;
	}
}
