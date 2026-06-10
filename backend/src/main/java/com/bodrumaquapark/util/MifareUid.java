package com.bodrumaquapark.util;

import java.math.BigInteger;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * Mifare (ISO 14443) kart UID normalizasyonu — kanonik depolama ve güvenli arama.
 * <p>
 * Kanonik form: büyük harf, bayt hizalı sıfır dolgulu hex (4 bayt = 8 karakter, 7 bayt = 14 karakter).
 */
public final class MifareUid {

	public static final int BYTES_SINGLE = 4;

	public static final int BYTES_DOUBLE = 7;

	public static final int HEX_SINGLE = BYTES_SINGLE * 2;

	public static final int HEX_DOUBLE = BYTES_DOUBLE * 2;

	private MifareUid() {
	}

	public record Parsed(String canonical, List<String> lookupKeys) {
	}

	/**
	 * Ham okuyucu çıktısını doğrular ve kanonik + arama anahtarlarını üretir.
	 *
	 * @param raw                    okuyucu / istemci girdisi
	 * @param legacyDecimalLookup    true ise eski proximity ondalık UID eşleşmesi de denenir
	 * @param includeReversedBytes   true ise bayt-ters çevrilmiş hex varyantları da aranır
	 */
	public static Optional<Parsed> parse(String raw, boolean legacyDecimalLookup, boolean includeReversedBytes) {
		String stripped = strip(raw);
		if (stripped.isEmpty()) {
			return Optional.empty();
		}
		Optional<byte[]> bytesOpt = decodeBytes(stripped, legacyDecimalLookup);
		if (bytesOpt.isEmpty()) {
			return Optional.empty();
		}
		byte[] bytes = bytesOpt.get();
		if (bytes.length == 0 || !isValidMifareByteLength(bytes.length)) {
			return Optional.empty();
		}
		String canonical = toPaddedHex(bytes);
		Set<String> keys = new LinkedHashSet<>();
		keys.add(canonical);
		keys.add(stripLeadingZeros(canonical));
		keys.add(canonical.toLowerCase(Locale.ROOT));
		if (legacyDecimalLookup) {
			keys.add(new BigInteger(1, bytes).toString());
		}
		if (includeReversedBytes) {
			String reversed = reverseBytesHex(canonical);
			keys.add(reversed);
			keys.add(stripLeadingZeros(reversed));
			keys.add(reversed.toLowerCase(Locale.ROOT));
		}
		return Optional.of(new Parsed(canonical, List.copyOf(keys)));
	}

	public static boolean isPlausible(String raw) {
		return parse(raw, true, true).isPresent();
	}

	/** Loglarda tam UID sızdırmamak için maskeleme. */
	public static String mask(String uid) {
		if (uid == null || uid.isBlank()) {
			return "—";
		}
		String t = uid.trim();
		if (t.length() <= 6) {
			return "****";
		}
		return t.substring(0, 2) + "…" + t.substring(t.length() - 2);
	}

	private static String strip(String raw) {
		if (raw == null) {
			return "";
		}
		String t = raw.trim().replaceAll("\\s+", "");
		if (t.regionMatches(true, 0, "0X", 0, 2)) {
			t = t.substring(2);
		}
		return t.toUpperCase(Locale.ROOT);
	}

	private static Optional<byte[]> decodeBytes(String stripped, boolean legacyDecimalLookup) {
		boolean allDigits = stripped.chars().allMatch(Character::isDigit);
		boolean hasHexLetter = false;
		for (int i = 0; i < stripped.length(); i++) {
			char c = stripped.charAt(i);
			if (c >= 'A' && c <= 'F') {
				hasHexLetter = true;
				break;
			}
		}
		/* Yalnızca 0-9 içeren diziler hem ondalık hem hex olabilir — önce legacy ondalık (proximity). */
		if (legacyDecimalLookup && allDigits) {
			try {
				BigInteger bi = new BigInteger(stripped);
				if (bi.signum() < 0) {
					return Optional.empty();
				}
				byte[] raw = bi.toByteArray();
				byte[] normalized = normalizeBigIntegerBytes(raw);
				if (isValidMifareByteLength(normalized.length)) {
					return Optional.of(normalized);
				}
			} catch (Exception e) {
				return Optional.empty();
			}
		}
		if (isHex(stripped) && (hasHexLetter || !allDigits)) {
			return Optional.of(hexToBytes(stripped));
		}
		if (isHex(stripped) && allDigits) {
			return Optional.of(hexToBytes(stripped));
		}
		return Optional.empty();
	}

	private static byte[] normalizeBigIntegerBytes(byte[] raw) {
		if (raw.length > 1 && raw[0] == 0) {
			byte[] trimmed = new byte[raw.length - 1];
			System.arraycopy(raw, 1, trimmed, 0, trimmed.length);
			raw = trimmed;
		}
		if (raw.length <= BYTES_SINGLE) {
			byte[] out = new byte[BYTES_SINGLE];
			System.arraycopy(raw, 0, out, BYTES_SINGLE - raw.length, raw.length);
			return out;
		}
		if (raw.length <= BYTES_DOUBLE) {
			byte[] out = new byte[BYTES_DOUBLE];
			System.arraycopy(raw, 0, out, BYTES_DOUBLE - raw.length, raw.length);
			return out;
		}
		return raw;
	}

	private static boolean isValidMifareByteLength(int len) {
		return len == BYTES_SINGLE || len == BYTES_DOUBLE;
	}

	private static boolean isHex(String s) {
		if (s.length() < 2) {
			return false;
		}
		for (int i = 0; i < s.length(); i++) {
			char c = s.charAt(i);
			if (!((c >= '0' && c <= '9') || (c >= 'A' && c <= 'F'))) {
				return false;
			}
		}
		return true;
	}

	private static byte[] hexToBytes(String hex) {
		String h = stripLeadingZeros(hex);
		int byteLen = (h.length() + 1) / 2;
		if (byteLen > BYTES_DOUBLE) {
			return new byte[0];
		}
		if (byteLen != BYTES_SINGLE && byteLen != BYTES_DOUBLE) {
			if (byteLen < BYTES_SINGLE) {
				byteLen = BYTES_SINGLE;
			} else {
				byteLen = BYTES_DOUBLE;
			}
		}
		byte[] out = new byte[byteLen];
		String padded = h.length() % 2 == 1 ? "0" + h : h;
		while (padded.length() < byteLen * 2) {
			padded = "0" + padded;
		}
		if (padded.length() > byteLen * 2) {
			padded = padded.substring(padded.length() - byteLen * 2);
		}
		for (int i = 0; i < byteLen; i++) {
			out[i] = (byte) Integer.parseInt(padded.substring(i * 2, i * 2 + 2), 16);
		}
		return out;
	}

	private static String toPaddedHex(byte[] bytes) {
		StringBuilder sb = new StringBuilder(bytes.length * 2);
		for (byte b : bytes) {
			sb.append(String.format(Locale.ROOT, "%02X", b & 0xFF));
		}
		return sb.toString();
	}

	private static String stripLeadingZeros(String hex) {
		String h = hex.toUpperCase(Locale.ROOT);
		int i = 0;
		while (i < h.length() - 1 && h.charAt(i) == '0') {
			i++;
		}
		return h.substring(i);
	}

	private static String reverseBytesHex(String paddedHex) {
		int byteLen = paddedHex.length() / 2;
		StringBuilder sb = new StringBuilder(paddedHex.length());
		for (int i = byteLen - 1; i >= 0; i--) {
			sb.append(paddedHex, i * 2, i * 2 + 2);
		}
		return sb.toString();
	}

}
