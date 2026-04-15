package com.bodrumaquapark.exception;

public class CardBlockedException extends RuntimeException {

	public CardBlockedException(String uid) {
		super("Kart bloke: " + uid);
	}
}
