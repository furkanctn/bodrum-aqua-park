package com.bodrumaquapark.web.dto;

import java.math.BigDecimal;

/** Gün sonu raporu — POS kart satış / bilet tahsilatı. */
public record TicketSalesReportSectionDto(
		BigDecimal cashTotal,
		BigDecimal cardTotal,
		long totalTicketCount,
		long childTicketCount,
		long adultTicketCount) {
}
