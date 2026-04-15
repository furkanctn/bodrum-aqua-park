package com.bodrumaquapark.exception;

public class DuplicateCardUidException extends RuntimeException {

	public DuplicateCardUidException(String uid) {
		super("Bu kart UID zaten kayıtlı: " + uid);
	}
}
