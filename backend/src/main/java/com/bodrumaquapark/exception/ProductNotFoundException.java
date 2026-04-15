package com.bodrumaquapark.exception;

public class ProductNotFoundException extends RuntimeException {

	public ProductNotFoundException(Long productId) {
		super("Ürün bulunamadı: " + productId);
	}
}
