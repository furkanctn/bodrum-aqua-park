package com.bodrumaquapark.service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.bodrumaquapark.access.ClientAccessMeta;
import com.bodrumaquapark.access.DeviceTimingGuard;
import com.bodrumaquapark.entity.AccessLog;
import com.bodrumaquapark.entity.CardPass;
import com.bodrumaquapark.entity.PassType;
import com.bodrumaquapark.entity.RfidCard;
import com.bodrumaquapark.entity.TurnstileDevice;
import com.bodrumaquapark.repository.AccessLogRepository;
import com.bodrumaquapark.repository.CardPassRepository;
import com.bodrumaquapark.repository.RfidCardRepository;
import com.bodrumaquapark.repository.TurnstileDeviceRepository;
import com.bodrumaquapark.util.RfidCardIds;
import com.bodrumaquapark.web.dto.AccessCheckRequest;
import com.bodrumaquapark.web.dto.AccessCheckResponse;
import com.bodrumaquapark.web.dto.AccessLogEntryResponse;
import com.bodrumaquapark.web.dto.AssignPassRequest;
import com.bodrumaquapark.web.dto.AssignPassResponse;
import com.bodrumaquapark.web.dto.RfidCardStatusResponse;

@Service
public class AccessControlService {

	private static final Logger log = LoggerFactory.getLogger(AccessControlService.class);

	private static final ZoneId PARK_ZONE = ZoneId.of("Europe/Istanbul");

	private static final String MSG_OK = "Geçiş onaylandı";

	private final RfidCardRepository rfidCardRepository;
	private final CardPassRepository cardPassRepository;
	private final AccessLogRepository accessLogRepository;
	private final TurnstileDeviceRepository turnstileDeviceRepository;
	private final PasswordEncoder passwordEncoder;

	public AccessControlService(
			RfidCardRepository rfidCardRepository,
			CardPassRepository cardPassRepository,
			AccessLogRepository accessLogRepository,
			TurnstileDeviceRepository turnstileDeviceRepository,
			PasswordEncoder passwordEncoder) {
		this.rfidCardRepository = rfidCardRepository;
		this.cardPassRepository = cardPassRepository;
		this.accessLogRepository = accessLogRepository;
		this.turnstileDeviceRepository = turnstileDeviceRepository;
		this.passwordEncoder = passwordEncoder;
	}

	@Transactional
	public AssignPassResponse assignPass(String operatorUserId, AssignPassRequest request) {
		if (operatorUserId == null || operatorUserId.isBlank()) {
			throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Oturum gerekli");
		}
		String cardId = RfidCardIds.normalize(request.cardId());
		if (cardId.isEmpty()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Kart kimliği boş olamaz");
		}
		PassType passType = request.passType() != null ? request.passType() : PassType.DAILY_SINGLE_ENTRY;
		LocalDate today = LocalDate.now(PARK_ZONE);

		RfidCard card = rfidCardRepository.findByCardId(cardId).orElseGet(() -> rfidCardRepository.save(new RfidCard(cardId, true)));
		if (!card.isActive()) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "Kart pasif; önce kartı yeniden aktifleştirin");
		}

		CardPass pass = cardPassRepository
				.findByRfidCard_IdAndValidDateAndPassType(card.getId(), today, passType)
				.orElseGet(() -> cardPassRepository.save(new CardPass(card, today, passType, true, false)));
		pass.setActive(true);
		if (passType == PassType.DAILY_SINGLE_ENTRY) {
			pass.setUsed(false);
		}
		cardPassRepository.save(pass);

		return new AssignPassResponse(cardId, today, passType, "Günlük geçiş hakkı tanımlandı");
	}

	@Transactional
	public void recordMissingDeviceToken(AccessCheckRequest request, ClientAccessMeta meta) {
		String deviceId = request.deviceId() != null ? request.deviceId().trim() : "";
		String normalizedCardId = RfidCardIds.normalize(request.cardId());
		persistLog(
				normalizedCardId.isEmpty() ? "—" : normalizedCardId,
				deviceId.isEmpty() ? "—" : deviceId,
				null,
				false,
				"X-DEVICE-TOKEN gerekli",
				meta != null ? meta : new ClientAccessMeta("", ""));
		log.warn(
				"access.check outcome=DENY deviceId={} cardIdHint={} ip={} reason=missing-device-token",
				deviceId,
				cardIdMask(normalizedCardId.isEmpty() ? "—" : normalizedCardId),
				meta != null ? meta.clientIp() : "");
	}

	@Transactional
	public AccessCheckResponse checkAccess(AccessCheckRequest request, String deviceTokenHeader, ClientAccessMeta meta) {
		ClientAccessMeta m = meta != null ? meta : new ClientAccessMeta("", "");
		String deviceId = request.deviceId() != null ? request.deviceId().trim() : "";
		String token = deviceTokenHeader != null ? deviceTokenHeader.trim() : "";
		String normalizedCardId = RfidCardIds.normalize(request.cardId());

		if (deviceId.isEmpty()) {
			return denyWithoutDeviceAuth(
					normalizedCardId.isEmpty() ? "—" : normalizedCardId, "", "Cihaz kimliği boş", m);
		}
		if (token.isEmpty()) {
			return denyWithoutDeviceAuth(
					normalizedCardId.isEmpty() ? "—" : normalizedCardId, deviceId, "X-DEVICE-TOKEN eksik", m);
		}

		Optional<TurnstileDevice> deviceOpt = turnstileDeviceRepository.findByDeviceIdAndActiveIsTrue(deviceId);
		Optional<String> storedHash = deviceOpt.map(TurnstileDevice::getDeviceTokenHash).filter(h -> !h.isBlank());
		boolean tokenMatches = DeviceTimingGuard.matchesToken(passwordEncoder, token, storedHash);
		if (deviceOpt.isEmpty() || !tokenMatches) {
			String reason = deviceOpt.isEmpty() ? "Bilinmeyen veya pasif cihaz" : "Geçersiz cihaz anahtarı";
			persistLog(
					normalizedCardId.isEmpty() ? "—" : normalizedCardId,
					deviceId,
					deviceOpt.orElse(null),
					false,
					reason,
					m);
			logOutcome(deviceId, normalizedCardId, false, reason, m);
			return new AccessCheckResponse(false, reason);
		}

		TurnstileDevice device = deviceOpt.get();

		if (normalizedCardId.isEmpty()) {
			return finishAuthenticated(device, "—", false, "Kart kimliği boş", "Kart kimliği boş", m);
		}

		Optional<RfidCard> cardOpt = rfidCardRepository.findByCardId(normalizedCardId);
		if (cardOpt.isEmpty()) {
			return finishAuthenticated(device, normalizedCardId, false, "Kart kayıtlı değil", "Kart kayıtlı değil", m);
		}
		RfidCard card = cardOpt.get();
		if (!card.isActive()) {
			return finishAuthenticated(device, normalizedCardId, false, "Kart pasif", "Kart pasif", m);
		}

		LocalDate today = LocalDate.now(PARK_ZONE);
		List<CardPass> passes = cardPassRepository.findByRfidCard_IdAndValidDate(card.getId(), today);
		Optional<CardPass> unlimited = passes.stream()
				.filter(p -> p.isActive() && p.getPassType() == PassType.DAILY_UNLIMITED)
				.findFirst();
		if (unlimited.isPresent()) {
			return finishAuthenticated(device, normalizedCardId, true, MSG_OK, MSG_OK, m);
		}

		Optional<CardPass> single = passes.stream()
				.filter(p -> p.isActive() && p.getPassType() == PassType.DAILY_SINGLE_ENTRY && !p.isUsed())
				.sorted(Comparator.comparing(CardPass::getId))
				.findFirst();
		if (single.isEmpty()) {
			return finishAuthenticated(
					device,
					normalizedCardId,
					false,
					"Geçerli günlük pas yok veya kullanılmış",
					"Geçerli günlük pas yok veya kullanılmış",
					m);
		}

		int updated = cardPassRepository.markConsumedIfUnused(single.get().getId(), Instant.now());
		if (updated != 1) {
			return finishAuthenticated(device, normalizedCardId, false, "Pas kullanılmış", "Pas kullanılmış", m);
		}
		return finishAuthenticated(device, normalizedCardId, true, MSG_OK, MSG_OK, m);
	}

	@Transactional(readOnly = true)
	public RfidCardStatusResponse getCardStatus(String rawCardId) {
		String cardId = RfidCardIds.normalize(rawCardId);
		if (cardId.isEmpty()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Kart kimliği boş olamaz");
		}
		RfidCard card = rfidCardRepository.findByCardId(cardId).orElseThrow(
				() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Kart bulunamadı"));
		LocalDate today = LocalDate.now(PARK_ZONE);
		List<CardPass> passes = cardPassRepository.findByRfidCard_IdAndValidDate(card.getId(), today);
		List<RfidCardStatusResponse.PassSlice> slices = passes.stream()
				.map(p -> new RfidCardStatusResponse.PassSlice(p.getPassType(), p.getValidDate(), p.isActive(), p.isUsed()))
				.toList();
		return new RfidCardStatusResponse(card.getCardId(), card.isActive(), slices);
	}

	@Transactional(readOnly = true)
	public Page<AccessLogEntryResponse> listAccessLogs(
			Instant fromInclusive,
			Instant toExclusive,
			String deviceIdFilter,
			String cardIdFilter,
			Pageable pageable) {
		String d = deviceIdFilter != null ? deviceIdFilter.trim() : "";
		String c = cardIdFilter != null ? RfidCardIds.normalize(cardIdFilter) : "";
		Page<AccessLog> page;
		if (!d.isEmpty() && !c.isEmpty()) {
			page = accessLogRepository.findByDeviceIdAndCardIdAndCreatedAtBetweenOrderByCreatedAtDesc(d, c, fromInclusive, toExclusive, pageable);
		} else if (!d.isEmpty()) {
			page = accessLogRepository.findByDeviceIdAndCreatedAtBetweenOrderByCreatedAtDesc(d, fromInclusive, toExclusive, pageable);
		} else if (!c.isEmpty()) {
			page = accessLogRepository.findByCardIdAndCreatedAtBetweenOrderByCreatedAtDesc(c, fromInclusive, toExclusive, pageable);
		} else {
			page = accessLogRepository.findByCreatedAtBetweenOrderByCreatedAtDesc(fromInclusive, toExclusive, pageable);
		}
		return page.map(
				row -> new AccessLogEntryResponse(
						row.getId(),
						row.getCardId(),
						row.getDeviceId(),
						row.isAllowed(),
						row.getReason(),
						row.getIpAddress(),
						row.getUserAgent(),
						row.getCreatedAt()));
	}

	private AccessCheckResponse denyWithoutDeviceAuth(String cardId, String deviceId, String reason, ClientAccessMeta meta) {
		persistLog(cardId, deviceId, null, false, reason, meta);
		logOutcome(deviceId, cardId, false, reason, meta);
		return new AccessCheckResponse(false, reason);
	}

	private AccessCheckResponse finishAuthenticated(
			TurnstileDevice device,
			String cardIdLogged,
			boolean passageAllowed,
			String responseMessage,
			String reason,
			ClientAccessMeta meta) {
		persistLog(cardIdLogged, device.getDeviceId(), device, passageAllowed, reason, meta);
		touchDeviceHeartbeat(device, passageAllowed);
		logOutcome(device.getDeviceId(), cardIdLogged, passageAllowed, reason, meta);
		return new AccessCheckResponse(passageAllowed, responseMessage);
	}

	private void touchDeviceHeartbeat(TurnstileDevice device, boolean passageAllowed) {
		Instant now = Instant.now();
		device.setLastSeenAt(now);
		if (passageAllowed) {
			device.setLastSuccessfulAccessAt(now);
		}
		turnstileDeviceRepository.save(device);
	}

	private void persistLog(
			String cardId,
			String deviceId,
			TurnstileDevice turnstileDevice,
			boolean allowed,
			String reason,
			ClientAccessMeta meta) {
		String ip = meta.clientIp() != null ? meta.clientIp() : "";
		String ua = meta.userAgent() != null ? meta.userAgent() : "";
		accessLogRepository.save(new AccessLog(cardId, deviceId, turnstileDevice, allowed, reason, ip, ua));
	}

	private void logOutcome(String deviceId, String cardId, boolean allowed, String reason, ClientAccessMeta meta) {
		String hint = cardIdMask(cardId);
		if (allowed) {
			log.info(
					"access.check outcome=ALLOW deviceId={} cardIdHint={} ip={} reason={}",
					deviceId,
					hint,
					meta.clientIp(),
					reason);
		} else {
			log.warn(
					"access.check outcome=DENY deviceId={} cardIdHint={} ip={} reason={}",
					deviceId,
					hint,
					meta.clientIp(),
					reason);
		}
	}

	private static String cardIdMask(String normalizedCardId) {
		if (normalizedCardId == null || normalizedCardId.isEmpty() || "—".equals(normalizedCardId)) {
			return "—";
		}
		if (normalizedCardId.length() <= 6) {
			return "****";
		}
		return normalizedCardId.substring(0, 2) + "…" + normalizedCardId.substring(normalizedCardId.length() - 2);
	}
}
