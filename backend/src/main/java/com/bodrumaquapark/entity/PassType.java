package com.bodrumaquapark.entity;

/**
 * Turnike geçiş pas türü.
 */
public enum PassType {
	/** Gün içinde tek geçiş; onayda {@code used=true} yapılır. */
	DAILY_SINGLE_ENTRY,
	/** Aynı gün içinde sınırsız geçiş; {@code used} alanı dikkate alınmaz. */
	DAILY_UNLIMITED
}
