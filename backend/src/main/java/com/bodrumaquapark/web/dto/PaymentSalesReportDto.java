package com.bodrumaquapark.web.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record PaymentSalesReportDto(
		LocalDate fromInclusive,
		LocalDate toInclusive,
		String timeZone,
		BigDecimal cashTotal,
		BigDecimal cardTotal,
		BigDecimal agencyTotal,
		BigDecimal grandTotal
) {
}
