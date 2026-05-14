package com.bodrumaquapark.service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bodrumaquapark.config.AccessGatewayProperties;
import com.bodrumaquapark.entity.TurnstileDevice;
import com.bodrumaquapark.repository.AccessLogRepository;
import com.bodrumaquapark.repository.TurnstileDeviceRepository;
import com.bodrumaquapark.web.dto.TurnstileDeviceStatusResponse;

@Service
public class TurnstileAdminService {

	private static final ZoneId PARK_ZONE = ZoneId.of("Europe/Istanbul");

	private final TurnstileDeviceRepository turnstileDeviceRepository;
	private final AccessLogRepository accessLogRepository;
	private final AccessGatewayProperties accessGatewayProperties;

	public TurnstileAdminService(
			TurnstileDeviceRepository turnstileDeviceRepository,
			AccessLogRepository accessLogRepository,
			AccessGatewayProperties accessGatewayProperties) {
		this.turnstileDeviceRepository = turnstileDeviceRepository;
		this.accessLogRepository = accessLogRepository;
		this.accessGatewayProperties = accessGatewayProperties;
	}

	@Transactional(readOnly = true)
	public List<TurnstileDeviceStatusResponse> listDeviceStatuses() {
		Instant startOfDay = LocalDate.now(PARK_ZONE).atStartOfDay(PARK_ZONE).toInstant();
		Instant now = Instant.now();
		int thresholdSec = Math.max(30, accessGatewayProperties.getDeviceOnlineThresholdSeconds());
		Instant onlineSince = now.minusSeconds(thresholdSec);

		List<TurnstileDevice> devices = turnstileDeviceRepository.findAllByOrderByDeviceIdAsc();
		return devices.stream().map(d -> toRow(d, startOfDay, now, onlineSince)).toList();
	}

	private TurnstileDeviceStatusResponse toRow(TurnstileDevice d, Instant startOfDay, Instant now, Instant onlineSince) {
		long failed = accessLogRepository.countByDeviceIdAndAllowedFalseAndCreatedAtBetween(d.getDeviceId(), startOfDay, now);
		boolean online = d.getLastSeenAt() != null && !d.getLastSeenAt().isBefore(onlineSince);
		return new TurnstileDeviceStatusResponse(
				d.getDeviceId(),
				d.getLabel(),
				d.isActive(),
				online,
				d.getLastSeenAt(),
				d.getLastSuccessfulAccessAt(),
				failed);
	}
}
