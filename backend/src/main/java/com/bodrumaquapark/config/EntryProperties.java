package com.bodrumaquapark.config;

import java.math.BigDecimal;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.entry")
public class EntryProperties {

	/**
	 * Turnikeden geçişte karttan düşülecek tutar (0 = sadece bakiye > 0 kontrolü için minimum kullanılabilir).
	 */
	private BigDecimal fee = new BigDecimal("50.00");

	public BigDecimal getFee() {
		return fee;
	}

	public void setFee(BigDecimal fee) {
		this.fee = fee;
	}
}
