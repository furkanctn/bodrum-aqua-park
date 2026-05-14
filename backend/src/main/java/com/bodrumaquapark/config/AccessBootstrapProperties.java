package com.bodrumaquapark.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.access.bootstrap")
public class AccessBootstrapProperties {

	/**
	 * Boş değilse uygulama açılışında bu kimlikte aktif bir turnike cihazı yoksa oluşturulur (geliştirme / ilk kurulum).
	 */
	private String deviceId = "";

	/**
	 * Düz metin; veritabanında BCrypt ile saklanır. Üretimde güçlü değer ve HTTPS kullanın.
	 */
	private String deviceToken = "";

	public String getDeviceId() {
		return deviceId;
	}

	public void setDeviceId(String deviceId) {
		this.deviceId = deviceId;
	}

	public String getDeviceToken() {
		return deviceToken;
	}

	public void setDeviceToken(String deviceToken) {
		this.deviceToken = deviceToken;
	}
}
