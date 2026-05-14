package com.bodrumaquapark.web.dto;

import java.time.Instant;

public record AccessLogEntryResponse(
		Long id,
		String cardId,
		String deviceId,
		boolean allowed,
		String reason,
		String ipAddress,
		String userAgent,
		Instant createdAt) {
}
