package com.bodrumaquapark.exception;

public class CardNotFoundException extends RuntimeException {

	public CardNotFoundException(String uid) {
		super("Kart bulunamadı: " + uid);
	}
}
