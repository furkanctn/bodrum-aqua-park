package com.bodrumaquapark.web.dto;

import java.time.LocalDate;

import com.bodrumaquapark.entity.PassType;

public record AssignPassResponse(String cardId, LocalDate validDate, PassType passType, String message) {
}
