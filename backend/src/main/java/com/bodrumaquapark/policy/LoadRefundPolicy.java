package com.bodrumaquapark.policy;

import java.math.BigDecimal;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import com.bodrumaquapark.entity.TransactionType;
import com.bodrumaquapark.util.Money;

/**
 * İş kuralı: kredi kartı ile yapılan bakiye yüklemeleri iade kapsamı dışındadır; yalnızca nakit (ve acenta kur)
 * yüklemeleri nakit iade defterine konu olabilir. Gelecekteki iade uç noktalarında kullanılmalıdır.
 */
public final class LoadRefundPolicy {

	private LoadRefundPolicy() {
	}

	/** Bu yükleme türü bakiyeden nakit olarak iade edilebilir mi? */
	public static boolean isCashRefundableLoad(TransactionType loadType) {
		if (loadType == null) {
			return false;
		}
		return loadType == TransactionType.LOAD_CASH || loadType == TransactionType.LOAD_AGENCY;
	}

	/** Kredi kartı / POS kart satırı ile yapılan yüklemeler için iade reddi. */
	public static void assertLoadRefundNotFromCardPayment(TransactionType originalLoadType) {
		if (originalLoadType == TransactionType.LOAD_CARD) {
			throw new ResponseStatusException(HttpStatus.CONFLICT,
					"Kredi kartı ile yapılan bakiye yüklemeleri iade edilemez; yalnızca nakit iade uygulanır.");
		}
	}

	/** POS ödeme anahtarı (nakit dışı) ile yapılan yüklemelerin iadesi engellenir. */
	public static void assertLoadRefundAllowedForPaymentMethod(String paymentMethodLowerCase) {
		String pm = paymentMethodLowerCase != null ? paymentMethodLowerCase.trim().toLowerCase() : "";
		if ("card".equals(pm)) {
			throw new ResponseStatusException(HttpStatus.CONFLICT,
					"Kredi kartı yüklemesi iade edilemez; yalnızca nakit iade mümkündür.");
		}
	}

	/**
	 * Kart bakiyesinden nakit olarak iade edilebilecek tutar.
	 * Harcama önce nakit (ve acenta kur) yüklemelerinden düşülür; kredi kartı yüklemesi korunur.
	 */
	public static BigDecimal computeCashRefundableAmount(
			BigDecimal currentBalance,
			BigDecimal cashLoadTotal,
			BigDecimal agencyLoadTotal,
			BigDecimal totalSpent,
			BigDecimal refundTotal) {
		BigDecimal balance = Money.normalize(currentBalance);
		BigDecimal cashLoads = Money.normalize(cashLoadTotal).add(Money.normalize(agencyLoadTotal));
		BigDecimal spent = Money.normalize(totalSpent);
		BigDecimal refunded = Money.normalize(refundTotal);

		BigDecimal cashPool = cashLoads.subtract(refunded);
		if (cashPool.compareTo(BigDecimal.ZERO) < 0) {
			cashPool = BigDecimal.ZERO;
		}
		BigDecimal cashSpent = spent.min(cashPool);
		BigDecimal cashRemaining = cashPool.subtract(cashSpent);
		if (cashRemaining.compareTo(BigDecimal.ZERO) < 0) {
			cashRemaining = BigDecimal.ZERO;
		}
		BigDecimal refundable = balance.min(cashRemaining);
		if (refundable.compareTo(BigDecimal.ZERO) < 0) {
			refundable = BigDecimal.ZERO;
		}
		return Money.normalize(refundable);
	}
}
