package com.bodrumaquapark.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.beko-pos")
public class BekoPosProperties {

	/**
	 * false ise POS'a gönder uçları devre dışı kalır.
	 */
	private boolean enabled = true;

	/**
	 * BekoOkcServisi / TokenX Connect yerel servis kök adresi (örn. http://127.0.0.1:9001).
	 */
	private String baseUrl = "http://127.0.0.1:9001";

	/**
	 * Sepet gönderme yolu — Swagger'daki SendBasket uç noktasıyla eşleşmeli.
	 */
	private String sendBasketPath = "/api/Basket/SendBasket";

	/**
	 * Cihaz / bağlantı kontrolü (opsiyonel). Boşsa yalnızca baseUrl ping edilir.
	 */
	private String statusPath = "/api/Device/GetDeviceInfo";

	/** Token sepet JSON: KDV kısım numarası (cihazdaki sectionNo). */
	private int sectionNo = 1;

	/** Token sepet JSON: vergi yüzdesi × 100 (1000 = %10). */
	private int taxPercent = 1000;

	/** Tek satırlı satışlarda varsayılan ürün adı. */
	private String defaultItemName = "Satış";

	private int connectTimeoutMs = 8_000;

	/** POS'ta kart ödemesi uzun sürebilir. */
	private int readTimeoutMs = 180_000;

	public boolean isEnabled() {
		return enabled;
	}

	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}

	public String getBaseUrl() {
		return baseUrl;
	}

	public void setBaseUrl(String baseUrl) {
		this.baseUrl = baseUrl != null ? baseUrl.trim() : "";
	}

	public String getSendBasketPath() {
		return sendBasketPath;
	}

	public void setSendBasketPath(String sendBasketPath) {
		this.sendBasketPath = sendBasketPath != null ? sendBasketPath.trim() : "";
	}

	public String getStatusPath() {
		return statusPath;
	}

	public void setStatusPath(String statusPath) {
		this.statusPath = statusPath != null ? statusPath.trim() : "";
	}

	public int getSectionNo() {
		return sectionNo;
	}

	public void setSectionNo(int sectionNo) {
		this.sectionNo = sectionNo;
	}

	public int getTaxPercent() {
		return taxPercent;
	}

	public void setTaxPercent(int taxPercent) {
		this.taxPercent = taxPercent;
	}

	public String getDefaultItemName() {
		return defaultItemName;
	}

	public void setDefaultItemName(String defaultItemName) {
		this.defaultItemName = defaultItemName != null ? defaultItemName : "Satış";
	}

	public int getConnectTimeoutMs() {
		return connectTimeoutMs;
	}

	public void setConnectTimeoutMs(int connectTimeoutMs) {
		this.connectTimeoutMs = connectTimeoutMs;
	}

	public int getReadTimeoutMs() {
		return readTimeoutMs;
	}

	public void setReadTimeoutMs(int readTimeoutMs) {
		this.readTimeoutMs = readTimeoutMs;
	}
}
