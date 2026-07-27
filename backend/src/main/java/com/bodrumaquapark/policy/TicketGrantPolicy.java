package com.bodrumaquapark.policy;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import com.bodrumaquapark.entity.Card;
import com.bodrumaquapark.entity.TransactionType;
import com.bodrumaquapark.web.dto.TicketGrantLineRequest;

/**
 * Bir fiziksel karta aynı İstanbul gününde yalnızca tek bilet yüklenmesini zorunlu kılar
 * (turnike entryGate=1 veya bugün TICKET_* ledger kaydı). Geçmiş günlerin ledger kayıtları engel değildir.
 */
public final class TicketGrantPolicy {

	public static final Set<TransactionType> TICKET_TYPES = EnumSet.of(
			TransactionType.TICKET_CASH,
			TransactionType.TICKET_CARD,
			TransactionType.TICKET_CREDIT);

	private TicketGrantPolicy() {
	}

	public static void assertTicketGrantAllowed(Card card, List<TicketGrantLineRequest> lines, boolean legacyPaidGrant) {
		if (card == null) {
			return;
		}
		if (card.getEntryGate() == 1) {
			throw conflict("Bu kartta kullanılmayan bilet giriş hakkı var.");
		}
		if (resolveTicketQuantity(lines, legacyPaidGrant) > 1) {
			throw conflict("Bir karta yalnızca tek bilet yüklenebilir.");
		}
	}

	/** {@code hasTicketLedgerToday}: yalnızca bugünkü TICKET_* kaydı varsa true olmalı. */
	public static void assertNoPriorTicketLoad(boolean hasTicketLedgerToday) {
		if (hasTicketLedgerToday) {
			throw conflict("Bu karta bugün zaten bilet yüklenmiş.");
		}
	}

	static int resolveTicketQuantity(List<TicketGrantLineRequest> lines, boolean legacyPaidGrant) {
		if (lines == null || lines.isEmpty()) {
			return legacyPaidGrant ? 1 : 0;
		}
		int total = 0;
		for (TicketGrantLineRequest line : lines) {
			if (line == null || line.ticketAgeGroupId() == null) {
				continue;
			}
			total += Math.max(1, line.quantity());
		}
		return total;
	}

	private static ResponseStatusException conflict(String message) {
		return new ResponseStatusException(HttpStatus.CONFLICT, message);
	}
}
