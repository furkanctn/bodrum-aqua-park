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

	/**
	 * true: yeni kart kayıtları ve arama Mifare UID formatını zorunlu kılar (önerilen, üretim).
	 * false: yalnızca geliştirme / geçiş dönemi.
	 */
	private boolean mifareStrictValidation = true;

	/**
	 * Eski proximity ondalık UID ile kayıtlı kartların okunmasına izin verir (geçiş dönemi).
	 */
	private boolean legacyDecimalLookup = true;

	/**
	 * Okuyucunun bayt-ters (LSB-first) hex göndermesi durumunda alternatif eşleşme dener.
	 */
	private boolean reverseByteOrderLookup = true;

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

	public boolean isMifareStrictValidation() {
		return mifareStrictValidation;
	}

	public void setMifareStrictValidation(boolean mifareStrictValidation) {
		this.mifareStrictValidation = mifareStrictValidation;
	}

	public boolean isLegacyDecimalLookup() {
		return legacyDecimalLookup;
	}

	public void setLegacyDecimalLookup(boolean legacyDecimalLookup) {
		this.legacyDecimalLookup = legacyDecimalLookup;
	}

	public boolean isReverseByteOrderLookup() {
		return reverseByteOrderLookup;
	}

	public void setReverseByteOrderLookup(boolean reverseByteOrderLookup) {
		this.reverseByteOrderLookup = reverseByteOrderLookup;
	}
}
