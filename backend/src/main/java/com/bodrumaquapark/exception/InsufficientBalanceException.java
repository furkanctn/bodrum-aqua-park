package com.bodrumaquapark.exception;

import java.math.BigDecimal;

public class InsufficientBalanceException extends RuntimeException {

	private final BigDecimal balance;
	private final BigDecimal required;

	public InsufficientBalanceException(BigDecimal balance, BigDecimal required) {
		super("Yetersiz bakiye");
		this.balance = balance;
		this.required = required;
	}

	public BigDecimal getBalance() {
		return balance;
	}

	public BigDecimal getRequired() {
		return required;
	}
}
