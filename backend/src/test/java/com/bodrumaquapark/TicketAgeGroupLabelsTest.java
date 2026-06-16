package com.bodrumaquapark;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.bodrumaquapark.util.TicketAgeGroupLabels;

class TicketAgeGroupLabelsTest {

	@Test
	void isChildTariff_recognizesChildNames() {
		assertTrue(TicketAgeGroupLabels.isChildTariff("7–12 Yaş"));
		assertTrue(TicketAgeGroupLabels.isChildTariff("Acenta Çocuk"));
		assertFalse(TicketAgeGroupLabels.isChildTariff("Yetişkin"));
		assertFalse(TicketAgeGroupLabels.isChildTariff("Acenta Yetişkin"));
	}

	@Test
	void isAgencyTariff_recognizesAgencyNames() {
		assertTrue(TicketAgeGroupLabels.isAgencyTariff("Acenta Yetişkin"));
		assertTrue(TicketAgeGroupLabels.isAgencyTariff("Acenta Çocuk"));
		assertFalse(TicketAgeGroupLabels.isAgencyTariff("Yetişkin"));
	}
}
