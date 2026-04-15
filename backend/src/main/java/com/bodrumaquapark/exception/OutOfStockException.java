package com.bodrumaquapark.exception;

public class OutOfStockException extends RuntimeException {

	public OutOfStockException(String productName) {
		super("Stok yetersiz: " + productName);
	}
}
