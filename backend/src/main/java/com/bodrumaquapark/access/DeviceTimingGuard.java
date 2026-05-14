package com.bodrumaquapark.access;

import java.util.Optional;

import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Bilinmeyen cihazlarda bile BCrypt doğrulaması çalıştırılarak zamanlama kanalı daraltılır.
 */
public final class DeviceTimingGuard {

	/**
	 * Geçerli BCrypt formatı; düz metin token ile eşleşmez (her zaman false).
	 */
	public static final String BCRYPT_PLACEHOLDER_HASH = "$2a$10$dXJ3SW6G7P50lGmMkkmwe.20cQQubK3.HZWzG3YB1tlRy.fqvM/BG";

	private DeviceTimingGuard() {
	}

	public static boolean matchesToken(PasswordEncoder encoder, String rawToken, Optional<String> storedHashOpt) {
		String hash = storedHashOpt.filter(h -> h != null && !h.isBlank()).orElse(BCRYPT_PLACEHOLDER_HASH);
		return encoder.matches(rawToken, hash);
	}
}
