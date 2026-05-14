package com.bodrumaquapark.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Turnike HTTP katmanı: CORS, oran sınırı, çevrimiçi eşiği.
 */
@ConfigurationProperties(prefix = "app.access.http")
public class AccessGatewayProperties {

	/**
	 * Virgülle ayrı izinli kökenler. Boş = CORS devre dışı (same-origin / masaüstü WebView).
	 */
	private String corsAllowedOrigins = "";

	/**
	 * IP başına dakikada en fazla POST /api/access/check. 0 = kapalı.
	 */
	private int rateLimitRequestsPerMinute = 120;

	/**
	 * Son başarılı cihaz doğrulamasından bu kadar saniye geçmediyse çevrimiçi sayılır.
	 */
	private int deviceOnlineThresholdSeconds = 120;

	public String getCorsAllowedOrigins() {
		return corsAllowedOrigins;
	}

	public void setCorsAllowedOrigins(String corsAllowedOrigins) {
		this.corsAllowedOrigins = corsAllowedOrigins;
	}

	public int getRateLimitRequestsPerMinute() {
		return rateLimitRequestsPerMinute;
	}

	public void setRateLimitRequestsPerMinute(int rateLimitRequestsPerMinute) {
		this.rateLimitRequestsPerMinute = rateLimitRequestsPerMinute;
	}

	public int getDeviceOnlineThresholdSeconds() {
		return deviceOnlineThresholdSeconds;
	}

	public void setDeviceOnlineThresholdSeconds(int deviceOnlineThresholdSeconds) {
		this.deviceOnlineThresholdSeconds = deviceOnlineThresholdSeconds;
	}
}
