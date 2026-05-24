package com.bodrumaquapark.policy;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

class LoadRefundPolicyTest {

	@Test
	void refundable_allCashNoSpend() {
		assertThat(LoadRefundPolicy.computeCashRefundableAmount(
				bd("500"), bd("500"), bd("0"), bd("0"), bd("0")))
				.isEqualByComparingTo(bd("500"));
	}

	@Test
	void refundable_cardOnly_zero() {
		assertThat(LoadRefundPolicy.computeCashRefundableAmount(
				bd("500"), bd("0"), bd("0"), bd("0"), bd("0")))
				.isEqualByComparingTo(bd("0"));
	}

	@Test
	void refundable_mixedSpendConsumesCashFirst() {
		// 500 nakit + 500 k.kartı, 500 harcama → nakit biter, kalan k.kartı iade edilemez
		assertThat(LoadRefundPolicy.computeCashRefundableAmount(
				bd("500"), bd("500"), bd("0"), bd("500"), bd("0")))
				.isEqualByComparingTo(bd("0"));
	}

	@Test
	void refundable_mixedSpendBeyondCash_zero() {
		assertThat(LoadRefundPolicy.computeCashRefundableAmount(
				bd("400"), bd("500"), bd("0"), bd("600"), bd("0")))
				.isEqualByComparingTo(bd("0"));
	}

	@Test
	void refundable_afterPartialRefund() {
		assertThat(LoadRefundPolicy.computeCashRefundableAmount(
				bd("200"), bd("500"), bd("0"), bd("200"), bd("100")))
				.isEqualByComparingTo(bd("200"));
	}

	@Test
	void refundable_agencyCountsAsCash() {
		assertThat(LoadRefundPolicy.computeCashRefundableAmount(
				bd("300"), bd("0"), bd("300"), bd("0"), bd("0")))
				.isEqualByComparingTo(bd("300"));
	}

	private static BigDecimal bd(String s) {
		return new BigDecimal(s);
	}
}
