package com.bodrumaquapark.web.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record PaymentSalesReportDto(
		LocalDate fromInclusive,
		LocalDate toInclusive,
		String timeZone,
		BigDecimal cashTotal,
		BigDecimal cardTotal,
		BigDecimal agencyTotal,
		BigDecimal grandTotal,
		List<AgencyTicketCountDto> agencyTicketCounts,
		long agencyTicketTotalCount,
		long ticketEntryTotalCount) {
}
