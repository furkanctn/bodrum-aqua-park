package com.bodrumaquapark.web.dto;

import java.time.Instant;

public record TurnstileDeviceStatusResponse(
		String deviceId,
		String label,
		boolean active,
		boolean online,
		Instant lastSeenAt,
		Instant lastSuccessfulAccessAt,
		long failedAttemptsToday) {
}
