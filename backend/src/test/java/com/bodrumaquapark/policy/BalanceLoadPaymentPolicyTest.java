package com.bodrumaquapark.policy;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import com.bodrumaquapark.entity.TransactionType;

class BalanceLoadPaymentPolicyTest {

	@Test
	void firstCashLoad_locksCash() {
		assertEquals("cash", BalanceLoadPaymentPolicy.paymentMethodFromLoadType(TransactionType.LOAD_CASH).orElseThrow());
		assertDoesNotThrow(() -> BalanceLoadPaymentPolicy.assertBalanceLoadPaymentAllowed("cash", TransactionType.LOAD_CASH));
	}

	@Test
	void cashLockedCard_rejectsCardPayment() {
		ResponseStatusException ex = assertThrows(ResponseStatusException.class,
				() -> BalanceLoadPaymentPolicy.assertBalanceLoadPaymentAllowed("card", TransactionType.LOAD_CASH));
		assertEquals(409, ex.getStatusCode().value());
	}

	@Test
	void cardLockedCard_rejectsCashPayment() {
		ResponseStatusException ex = assertThrows(ResponseStatusException.class,
				() -> BalanceLoadPaymentPolicy.assertBalanceLoadPaymentAllowed("cash", TransactionType.LOAD_CARD));
		assertEquals(409, ex.getStatusCode().value());
	}
}
