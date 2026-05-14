package com.bodrumaquapark.web.dto;

import java.time.LocalDate;
import java.util.List;

import com.bodrumaquapark.entity.PassType;

public record RfidCardStatusResponse(String cardId, boolean active, List<PassSlice> passesToday) {

	public record PassSlice(PassType passType, LocalDate validDate, boolean active, boolean used) {
	}
}
