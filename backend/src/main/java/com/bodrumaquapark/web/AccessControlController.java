package com.bodrumaquapark.web;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.web.server.ResponseStatusException;

import com.bodrumaquapark.access.ClientAccessMeta;
import com.bodrumaquapark.entity.RoleCode;
import com.bodrumaquapark.security.DeviceTokenConstants;
import com.bodrumaquapark.security.JwtAuthenticationFilter;
import com.bodrumaquapark.service.AccessControlService;
import com.bodrumaquapark.web.dto.AccessCheckRequest;
import com.bodrumaquapark.web.dto.AccessCheckResponse;
import com.bodrumaquapark.web.dto.AccessLogEntryResponse;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

@RestController
@RequestMapping("/api/access")
public class AccessControlController {

	private static final Logger log = LoggerFactory.getLogger(AccessControlController.class);

	private static final ZoneId PARK_ZONE = ZoneId.of("Europe/Istanbul");

	private final AccessControlService accessControlService;

	public AccessControlController(AccessControlService accessControlService) {
		this.accessControlService = accessControlService;
	}

	@PostMapping("/check")
	public ResponseEntity<AccessCheckResponse> check(
			@Valid @RequestBody AccessCheckRequest request,
			@RequestHeader(value = DeviceTokenConstants.HEADER_NAME, required = false) String deviceToken,
			HttpServletRequest httpRequest) {
		if (deviceToken == null || deviceToken.isBlank()) {
			ClientAccessMeta meta = ClientAccessMeta.from(httpRequest);
			accessControlService.recordMissingDeviceToken(request, meta);
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
					.body(new AccessCheckResponse(false, "X-DEVICE-TOKEN gerekli"));
		}
		long t0 = System.nanoTime();
		ClientAccessMeta meta = ClientAccessMeta.from(httpRequest);
		try {
			AccessCheckResponse body = accessControlService.checkAccess(request, deviceToken, meta);
			long ms = (System.nanoTime() - t0) / 1_000_000L;
			log.info(
					"access.check.http deviceId={} allowed={} latencyMs={} ip={}",
					request.deviceId(),
					body.allowed(),
					ms,
					meta.clientIp());
			return ResponseEntity.ok(body);
		} catch (RuntimeException ex) {
			long ms = (System.nanoTime() - t0) / 1_000_000L;
			log.error("access.check.http error latencyMs={} ip={}", ms, meta.clientIp(), ex);
			throw ex;
		}
	}

	@GetMapping("/logs")
	public Page<AccessLogEntryResponse> logs(
			@RequestAttribute(JwtAuthenticationFilter.ATTR_ROLE) RoleCode role,
			@RequestParam(name = "from", required = false) Instant from,
			@RequestParam(name = "to", required = false) Instant to,
			@RequestParam(name = "deviceId", required = false) String deviceId,
			@RequestParam(name = "cardId", required = false) String cardId,
			@RequestParam(name = "page", required = false, defaultValue = "0") int page,
			@RequestParam(name = "size", required = false, defaultValue = "50") int size) {
		requireAdmin(role);
		Instant fromEff = from != null ? from : LocalDate.now(PARK_ZONE).atStartOfDay(PARK_ZONE).toInstant();
		Instant toEff = to != null ? to : Instant.now();
		if (toEff.isBefore(fromEff)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "'to' zamanı 'from'dan önce olamaz");
		}
		int safeSize = Math.min(Math.max(size, 1), 500);
		var pageable = PageRequest.of(Math.max(page, 0), safeSize, Sort.by(Sort.Direction.DESC, "createdAt"));
		return accessControlService.listAccessLogs(fromEff, toEff, deviceId, cardId, pageable);
	}

	private static void requireAdmin(RoleCode role) {
		if (role != RoleCode.ADMIN) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Bu işlem için yönetici yetkisi gerekir");
		}
	}
}
