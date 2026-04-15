package com.bodrumaquapark.web.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.bodrumaquapark.entity.Card;
import com.bodrumaquapark.entity.CardLedgerEntry;
import com.bodrumaquapark.entity.TransactionType;
import com.bodrumaquapark.util.Money;

/**
 * Ürün satış ekranı için kart + özet + hareketler.
 */
public record CardDetailResponse(
		String uid,
		Long cardId,
		BigDecimal balance,
		String status,
		Instant createdAt,
		Instant validFrom,
		Instant validTo,
		String definedBy,
		String booth,
		String tariff,
		int entryGate,
		BigDecimal totalLoaded,
		BigDecimal totalSpent,
		BigDecimal cashTotal,
		BigDecimal cardTotal,
		BigDecimal depositTotal,
		BigDecimal grandTotal,
		BigDecimal refundTotal,
		BigDecimal expectedBalance,
		List<LedgerEntryResponse> ledger
) {

	/**
	 * Defter satırlarından nakit / k.kartı / nakit kur yüklemeleri ve iade; meta alanlar açıklamalardan (varsa) çıkarılır.
	 */
	public static CardDetailResponse build(Card card, List<CardLedgerEntry> entries, List<LedgerEntryResponse> ledger) {
		BigDecimal totalLoaded = BigDecimal.ZERO;
		BigDecimal totalSpent = BigDecimal.ZERO;
		BigDecimal cash = BigDecimal.ZERO;
		BigDecimal cardPay = BigDecimal.ZERO;
		BigDecimal agency = BigDecimal.ZERO;
		BigDecimal refund = BigDecimal.ZERO;

		for (CardLedgerEntry e : entries) {
			BigDecimal ac = e.getAmountChange();
			if (ac.compareTo(BigDecimal.ZERO) > 0) {
				totalLoaded = totalLoaded.add(ac);
				switch (e.getType()) {
					case LOAD_CASH -> cash = cash.add(ac);
					case LOAD_CARD -> cardPay = cardPay.add(ac);
					case LOAD_AGENCY -> agency = agency.add(ac);
					default -> {
					}
				}
			} else if (ac.compareTo(BigDecimal.ZERO) < 0) {
				totalSpent = totalSpent.add(ac.negate());
			}
			if (e.getType() == TransactionType.REFUND_CASH) {
				refund = refund.add(ac.abs());
			}
		}

		totalLoaded = Money.normalize(totalLoaded);
		totalSpent = Money.normalize(totalSpent);
		cash = Money.normalize(cash);
		cardPay = Money.normalize(cardPay);
		agency = Money.normalize(agency);
		refund = Money.normalize(refund);
		BigDecimal grand = Money.normalize(cash.add(cardPay).add(agency));
		BigDecimal expected = Money.normalize(card.getBalance());

		List<CardLedgerEntry> asc = new ArrayList<>(entries);
		asc.sort(Comparator.comparing(CardLedgerEntry::getCreatedAt));

		return new CardDetailResponse(
				card.getUid(),
				card.getId(),
				card.getBalance(),
				card.getStatus().name(),
				card.getCreatedAt(),
				card.getCreatedAt(),
				null,
				extractDefinedBy(asc),
				extractBooth(asc),
				extractTariff(asc),
				card.getEntryGate(),
				totalLoaded,
				totalSpent,
				cash,
				cardPay,
				agency,
				grand,
				refund,
				expected,
				ledger);
	}

	private static final Pattern TARIFF_IN_PARENS = Pattern.compile("\\(([^)]+)\\)");
	private static final Pattern BOOTH = Pattern.compile("GİŞE[-\\s]*[A-Za-z0-9]+", Pattern.CASE_INSENSITIVE);
	private static final Pattern DEFINED_BY = Pattern.compile("([0-9]{3,6})\\s*[—\\-]\\s*([^·…]+)");

	private static String extractTariff(List<CardLedgerEntry> asc) {
		for (CardLedgerEntry e : asc) {
			String d = e.getDescription();
			if (d == null || d.isBlank()) {
				continue;
			}
			String s = d.trim();
			if (s.contains("tanımlandı") || s.contains("Tanımlandı") || s.toLowerCase().contains("tarife")) {
				Matcher m = TARIFF_IN_PARENS.matcher(s);
				if (m.find()) {
					return m.group(1).trim();
				}
			}
		}
		return null;
	}

	private static String extractBooth(List<CardLedgerEntry> asc) {
		for (CardLedgerEntry e : asc) {
			String d = e.getDescription();
			if (d == null || d.isBlank()) {
				continue;
			}
			Matcher m = BOOTH.matcher(d);
			if (m.find()) {
				return m.group().trim();
			}
		}
		return null;
	}

	private static String extractDefinedBy(List<CardLedgerEntry> asc) {
		for (CardLedgerEntry e : asc) {
			String d = e.getDescription();
			if (d == null || d.isBlank()) {
				continue;
			}
			Matcher m = DEFINED_BY.matcher(d);
			if (m.find()) {
				String name = m.group(2).trim();
				if (name.length() > 80) {
					name = name.substring(0, 80).trim() + "…";
				}
				return m.group(1).trim() + " — " + name;
			}
		}
		return null;
	}
}
