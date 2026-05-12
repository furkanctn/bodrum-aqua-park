package com.bodrumaquapark.policy;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import com.bodrumaquapark.entity.TransactionType;

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
}
