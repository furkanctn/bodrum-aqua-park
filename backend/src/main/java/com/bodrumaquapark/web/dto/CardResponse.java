package com.bodrumaquapark.web.dto;

import java.math.BigDecimal;
import java.time.Instant;

import com.bodrumaquapark.entity.Card;
import com.bodrumaquapark.entity.CardStatus;

public record CardResponse(
		String uid,
		BigDecimal balance,
		CardStatus status,
		Instant createdAt,
		int entryGate
) {

	public static CardResponse from(Card card) {
		return new CardResponse(card.getUid(), card.getBalance(), card.getStatus(), card.getCreatedAt(),
				card.getEntryGate());
	}
}
