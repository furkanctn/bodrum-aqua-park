package com.bodrumaquapark.web.dto;

import java.time.LocalDate;
import java.util.List;

public record AdminDayCloseReportDto(
		LocalDate date,
		String timeZone,
		AdminSummaryReportDto summary,
		List<AdminDayLedgerLineDto> lines
) {
}
