package com.bodrumaquapark.web.dto;

import java.math.BigDecimal;

/** Gün sonu — kart sorgulama ekranından yapılan nakit iadeler. */
public record CardInquiryRefundReportSectionDto(BigDecimal cashRefundTotal, long refundCardCount) {
}
