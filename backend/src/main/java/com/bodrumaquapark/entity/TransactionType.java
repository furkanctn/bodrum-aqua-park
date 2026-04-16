package com.bodrumaquapark.entity;

public enum TransactionType {
	ENTRY,
	SALE,
	LOAD_CASH,
	LOAD_CARD,
	LOAD_AGENCY,
	/** POS bilet tahsilatı — kart bakiyesi değişmez; raporda nakit/kart/kredi ayrımı için */
	TICKET_CASH,
	TICKET_CARD,
	TICKET_CREDIT,
	REFUND_CASH
}
