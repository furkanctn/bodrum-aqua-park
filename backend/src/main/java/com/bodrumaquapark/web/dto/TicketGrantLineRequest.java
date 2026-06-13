package com.bodrumaquapark.web.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record TicketGrantLineRequest(
		@NotNull Long ticketAgeGroupId,
		@Min(1) int quantity) {
}
