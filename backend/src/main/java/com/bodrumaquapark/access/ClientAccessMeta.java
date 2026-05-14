package com.bodrumaquapark.access;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Turnike istemcisinin ağ meta verisi (günlük ve denetim).
 */
public record ClientAccessMeta(String clientIp, String userAgent) {

	private static final int MAX_UA_LEN = 512;

	public static ClientAccessMeta from(HttpServletRequest request) {
		if (request == null) {
			return new ClientAccessMeta("", "");
		}
		String ip = resolveClientIp(request);
		String ua = request.getHeader("User-Agent");
		if (ua == null) {
			ua = "";
		}
		if (ua.length() > MAX_UA_LEN) {
			ua = ua.substring(0, MAX_UA_LEN);
		}
		return new ClientAccessMeta(ip, ua);
	}

	private static String resolveClientIp(HttpServletRequest request) {
		String forwarded = request.getHeader("X-Forwarded-For");
		if (forwarded != null && !forwarded.isBlank()) {
			int comma = forwarded.indexOf(',');
			String first = comma > 0 ? forwarded.substring(0, comma) : forwarded;
			return first.trim();
		}
		String realIp = request.getHeader("X-Real-IP");
		if (realIp != null && !realIp.isBlank()) {
			return realIp.trim();
		}
		return request.getRemoteAddr() != null ? request.getRemoteAddr() : "";
	}
}
