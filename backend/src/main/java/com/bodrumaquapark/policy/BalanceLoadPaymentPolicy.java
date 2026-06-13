package com.bodrumaquapark.policy;

import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import com.bodrumaquapark.entity.TransactionType;

/**
 * Kartın ilk bakiye yüklemesindeki ödeme kanalı (nakit / kart) sonraki yüklemeler için kilitlenir.
 */
public final class BalanceLoadPaymentPolicy {

	private static final Set<TransactionType> BALANCE_LOAD_TYPES = EnumSet.of(
			TransactionType.LOAD_CASH,
			TransactionType.LOAD_CARD,
			TransactionType.LOAD_AGENCY);

	private BalanceLoadPaymentPolicy() {
	}

	public static boolean isBalanceLoadType(TransactionType type) {
		return type != null && BALANCE_LOAD_TYPES.contains(type);
	}

	/** İlk yükleme türünden POS ödeme kodu: cash | card */
	public static Optional<String> paymentMethodFromLoadType(TransactionType type) {
		if (type == null) {
			return Optional.empty();
		}
		return switch (type) {
			case LOAD_CASH, LOAD_AGENCY -> Optional.of("cash");
			case LOAD_CARD -> Optional.of("card");
			default -> Optional.empty();
		};
	}

	public static Optional<String> normalizeRequestedPaymentMethod(String paymentMethod) {
		if (paymentMethod == null || paymentMethod.isBlank()) {
			return Optional.empty();
		}
		String pm = paymentMethod.trim().toLowerCase();
		return switch (pm) {
			case "cash", "rate" -> Optional.of("cash");
			case "card" -> Optional.of("card");
			default -> Optional.empty();
		};
	}

	public static void assertBalanceLoadPaymentAllowed(String requestedPaymentMethod, TransactionType firstLoadType) {
		Optional<String> locked = paymentMethodFromLoadType(firstLoadType);
		if (locked.isEmpty()) {
			return;
		}
		Optional<String> requested = normalizeRequestedPaymentMethod(requestedPaymentMethod);
		if (requested.isEmpty() || locked.get().equals(requested.get())) {
			return;
		}
		String msg = "card".equals(locked.get())
				? "Bu kart kredi kartı ile bakiye yüklendiği için yalnızca kart ile ödeme kullanılabilir."
				: "Bu kart nakit ile bakiye yüklendiği için yalnızca nakit ödeme kullanılabilir.";
		throw new ResponseStatusException(HttpStatus.CONFLICT, msg);
	}
}
