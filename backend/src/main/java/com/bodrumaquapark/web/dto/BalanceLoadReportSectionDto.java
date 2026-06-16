package com.bodrumaquapark.web.dto;

import java.math.BigDecimal;

/** Gün sonu raporu — POS bakiye yükleme (resepsiyon) tahsilatı. */
public record BalanceLoadReportSectionDto(BigDecimal cashTotal, BigDecimal cardTotal, BigDecimal agencyTotal) {
}
