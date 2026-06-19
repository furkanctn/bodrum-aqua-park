package com.bodrumaquapark.policy;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import com.bodrumaquapark.entity.Card;
import com.bodrumaquapark.web.dto.TicketGrantLineRequest;

class TicketGrantPolicyTest {

	@Test
	void resolveTicketQuantity_singleLine() {
		assertEquals(1, TicketGrantPolicy.resolveTicketQuantity(
				List.of(new TicketGrantLineRequest(1L, 1)), false));
	}

	@Test
	void resolveTicketQuantity_multipleLines() {
		assertEquals(3, TicketGrantPolicy.resolveTicketQuantity(
				List.of(new TicketGrantLineRequest(1L, 2), new TicketGrantLineRequest(2L, 1)), false));
	}

	@Test
	void entryGateBlocksSecondGrant() {
		Card card = new Card("04ABCD01", java.math.BigDecimal.ZERO);
		card.setEntryGate(1);
		ResponseStatusException ex = assertThrows(ResponseStatusException.class,
				() -> TicketGrantPolicy.assertTicketGrantAllowed(card, List.of(new TicketGrantLineRequest(1L, 1)), false));
		assertEquals(409, ex.getStatusCode().value());
	}

	@Test
	void multipleTicketsRejected() {
		Card card = new Card("04ABCD02", java.math.BigDecimal.ZERO);
		ResponseStatusException ex = assertThrows(ResponseStatusException.class,
				() -> TicketGrantPolicy.assertTicketGrantAllowed(card,
						List.of(new TicketGrantLineRequest(1L, 2)), false));
		assertEquals(409, ex.getStatusCode().value());
	}

	@Test
	void priorTicketLoadRejected() {
		ResponseStatusException ex = assertThrows(ResponseStatusException.class,
				() -> TicketGrantPolicy.assertNoPriorTicketLoad(true));
		assertEquals(409, ex.getStatusCode().value());
		assertDoesNotThrow(() -> TicketGrantPolicy.assertNoPriorTicketLoad(false));
	}
}
