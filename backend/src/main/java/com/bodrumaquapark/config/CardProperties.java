package com.bodrumaquapark.config;

import java.math.BigDecimal;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.card")
public class CardProperties {

	/**
	 * Kart tanımlamada ilk bakiye 0 veya boş gönderildiğinde kullanılacak varsayılan (demo / DB yokken).
	 */
	private BigDecimal defaultInitialBalance = new BigDecimal("5000.00");

	/**
	 * Boş değilse uygulama açılışında bu UID için kart yoksa oluşturulur, varsa bakiye demo-balance olacak şekilde güncellenir (örn. POS demo 123).
	 */
	private String demoUid = "";

	/** {@link #demoUid} için hedef bakiye (TL). */
	private BigDecimal demoBalance = new BigDecimal("5000.00");

	public BigDecimal getDefaultInitialBalance() {
		return defaultInitialBalance;
	}

	public void setDefaultInitialBalance(BigDecimal defaultInitialBalance) {
		this.defaultInitialBalance = defaultInitialBalance;
	}

	public String getDemoUid() {
		return demoUid;
	}

	public void setDemoUid(String demoUid) {
		this.demoUid = demoUid;
	}

	public BigDecimal getDemoBalance() {
		return demoBalance;
	}

	public void setDemoBalance(BigDecimal demoBalance) {
		this.demoBalance = demoBalance;
	}
}
