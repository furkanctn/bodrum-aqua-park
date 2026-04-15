package com.bodrumaquapark.security;

public class InvalidTokenException extends RuntimeException {

	public InvalidTokenException(String message) {
		super(message);
	}
}
