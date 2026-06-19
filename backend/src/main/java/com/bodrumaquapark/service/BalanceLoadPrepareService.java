package com.bodrumaquapark.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.bodrumaquapark.util.Money;

@Service
public class BalanceLoadPrepareService {

	private static final long TTL_SECONDS = 300;

	private final Map<String, PendingPrepare> pending = new ConcurrentHashMap<>();

	public String prepare(String operatorUserId, BigDecimal amount, String paymentMethod) {
		String operator = normalizeOperator(operatorUserId);
		BigDecimal amt = Money.normalize(amount);
		String pm = normalizePaymentMethod(paymentMethod);
		if (amt.compareTo(BigDecimal.ZERO) <= 0) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tutar pozitif olmalıdır");
		}
		evictExpired();
		String token = UUID.randomUUID().toString();
		pending.put(token, new PendingPrepare(operator, amt, pm, Instant.now().plusSeconds(TTL_SECONDS)));
		return token;
	}

	public void consume(String token, String operatorUserId, BigDecimal amount, String paymentMethod) {
		if (token == null || token.isBlank()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
					"Bakiye yüklemesi onaylanmadı. Önce «Yüklemeyi tamamla» adımını tamamlayın.");
		}
		evictExpired();
		PendingPrepare entry = pending.remove(token.trim());
		if (entry == null) {
			throw new ResponseStatusException(HttpStatus.CONFLICT,
					"Bakiye yükleme onayı geçersiz veya süresi dolmuş. «Yüklemeyi tamamla» ile yeniden başlayın.");
		}
		if (entry.expiresAt().isBefore(Instant.now())) {
			throw new ResponseStatusException(HttpStatus.CONFLICT,
					"Bakiye yükleme onayının süresi doldu. «Yüklemeyi tamamla» ile yeniden başlayın.");
		}
		String operator = normalizeOperator(operatorUserId);
		if (!entry.operatorUserId().equals(operator)) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Bakiye yükleme onayı bu kasiyere ait değil.");
		}
		BigDecimal amt = Money.normalize(amount);
		String pm = normalizePaymentMethod(paymentMethod);
		if (entry.amount().compareTo(amt) != 0 || !entry.paymentMethod().equals(pm)) {
			throw new ResponseStatusException(HttpStatus.CONFLICT,
					"Onaylanan tutar veya ödeme yöntemi değişti. «Yüklemeyi tamamla» ile yeniden başlayın.");
		}
	}

	private void evictExpired() {
		Instant now = Instant.now();
		pending.entrySet().removeIf(e -> e.getValue().expiresAt().isBefore(now));
	}

	private static String normalizeOperator(String operatorUserId) {
		if (operatorUserId == null || operatorUserId.isBlank()) {
			return "—";
		}
		return operatorUserId.trim();
	}

	private static String normalizePaymentMethod(String paymentMethod) {
		if (paymentMethod == null || paymentMethod.isBlank()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ödeme yöntemi gerekli");
		}
		String pm = paymentMethod.trim().toLowerCase();
		if (!pm.equals("cash") && !pm.equals("card") && !pm.equals("rate")) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ödeme: cash, card veya rate");
		}
		return pm;
	}

	private record PendingPrepare(String operatorUserId, BigDecimal amount, String paymentMethod, Instant expiresAt) {
	}
}
