package com.bodrumaquapark.web.dto;

public record AccessCheckResponse(boolean allowed, String message) {
}
