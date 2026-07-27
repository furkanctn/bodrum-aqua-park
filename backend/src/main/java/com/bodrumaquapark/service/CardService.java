package com.bodrumaquapark.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.bodrumaquapark.config.CardProperties;
import com.bodrumaquapark.entity.Card;
import com.bodrumaquapark.entity.CardLedgerEntry;
import com.bodrumaquapark.entity.CardStatus;
import com.bodrumaquapark.entity.TicketAgeGroup;
import com.bodrumaquapark.entity.TransactionType;
import com.bodrumaquapark.exception.CardBlockedException;
import com.bodrumaquapark.exception.CardNotFoundException;
import com.bodrumaquapark.exception.DuplicateCardUidException;
import com.bodrumaquapark.policy.BalanceLoadPaymentPolicy;
import com.bodrumaquapark.policy.TicketGrantPolicy;
import com.bodrumaquapark.repository.CardLedgerEntryRepository;
import com.bodrumaquapark.repository.CardRepository;
import com.bodrumaquapark.repository.TicketAgeGroupRepository;
import com.bodrumaquapark.util.MifareUid;
import com.bodrumaquapark.util.Money;
import com.bodrumaquapark.web.dto.CardDetailResponse;
import com.bodrumaquapark.web.dto.CashRefundResponse;
import com.bodrumaquapark.web.dto.LedgerEntryResponse;
import com.bodrumaquapark.web.dto.TicketGrantLineRequest;

@Service
public class CardService {

	private static final Logger log = LoggerFactory.getLogger(CardService.class);

	/** Kart sorgulama / POS hareket listesi gün sınırı (raporlar tüm geçmişi kullanır). */
	public static final ZoneId PARK_ZONE = ZoneId.of("Europe/Istanbul");

	private static final EnumSet<TransactionType> BALANCE_LOAD_TYPES = EnumSet.of(
			TransactionType.LOAD_CASH,
			TransactionType.LOAD_CARD,
			TransactionType.LOAD_AGENCY);


	private final CardRepository cardRepository;
	private final CardLedgerEntryRepository ledgerEntryRepository;
	private final TicketAgeGroupRepository ticketAgeGroupRepository;
	private final CardProperties cardProperties;

	public CardService(CardRepository cardRepository, CardLedgerEntryRepository ledgerEntryRepository,
			TicketAgeGroupRepository ticketAgeGroupRepository, CardProperties cardProperties) {
		this.cardRepository = cardRepository;
		this.ledgerEntryRepository = ledgerEntryRepository;
		this.ticketAgeGroupRepository = ticketAgeGroupRepository;
		this.cardProperties = cardProperties;
	}

	private static final String MSG_FIRST_LOAD = "Ilk bakiye yukleme";

	@Transactional(readOnly = true)
	public Optional<String> resolveBalanceLoadPaymentMethod(String uid) {
		MifareUid.Parsed parsed = requireParsedUid(uid);
		return findCardByUidFlexible(parsed.canonical()).flatMap(this::resolveBalanceLoadPaymentMethodForCard);
	}

	private Optional<String> resolveBalanceLoadPaymentMethodForCard(Card card) {
		if (card == null || card.getId() == null) {
			return Optional.empty();
		}
		return ledgerEntryRepository
				.findFirstByCard_IdAndTypeInOrderByCreatedAtAscIdAsc(card.getId(), BALANCE_LOAD_TYPES)
				.flatMap(entry -> BalanceLoadPaymentPolicy.paymentMethodFromLoadType(entry.getType()));
	}

	private MifareUid.Parsed requireParsedUid(String raw) {
		String key = raw != null ? raw.trim() : "";
		if (key.isEmpty()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Kart UID gerekli");
		}
		if (isDemoUidBypass(key)) {
			return new MifareUid.Parsed(key, List.of(key));
		}
		Optional<MifareUid.Parsed> parsed = MifareUid.parse(
				key, cardProperties.isLegacyDecimalLookup(), cardProperties.isReverseByteOrderLookup());
		if (parsed.isEmpty()) {
			if (cardProperties.isMifareStrictValidation()) {
				throw new ResponseStatusException(
						HttpStatus.BAD_REQUEST,
						"Geçersiz Mifare UID — 4 veya 7 bayt hex / geçerli ondalık beklenir");
			}
			return new MifareUid.Parsed(key, List.of(key));
		}
		return parsed.get();
	}

	private Optional<MifareUid.Parsed> tryParseUid(String raw) {
		String key = raw != null ? raw.trim() : "";
		if (key.isEmpty()) {
			return Optional.empty();
		}
		if (isDemoUidBypass(key)) {
			return Optional.of(new MifareUid.Parsed(key, List.of(key)));
		}
		Optional<MifareUid.Parsed> parsed = MifareUid.parse(
				key, cardProperties.isLegacyDecimalLookup(), cardProperties.isReverseByteOrderLookup());
		if (parsed.isPresent()) {
			return parsed;
		}
		if (!cardProperties.isMifareStrictValidation()) {
			return Optional.of(new MifareUid.Parsed(key, List.of(key)));
		}
		return Optional.empty();
	}

	private boolean isDemoUidBypass(String key) {
		String demo = cardProperties.getDemoUid();
		return demo != null && !demo.isBlank() && demo.trim().equals(key);
	}

	private Optional<Card> findCardByUidFlexible(String raw) {
		return tryParseUid(raw).flatMap(p -> cardRepository.findFirstByUidIn(p.lookupKeys()));
	}

	@Transactional
	public Card findCardForUpdateByUidFlexible(String rawUid) {
		String key = rawUid != null ? rawUid.trim() : "";
		if (key.isEmpty()) {
			throw new CardNotFoundException(key);
		}
		return tryParseUid(key)
				.flatMap(p -> cardRepository.findFirstByUidInForUpdate(p.lookupKeys()))
				.orElseThrow(() -> new CardNotFoundException(key));
	}

	@Transactional
	public Card issueCard(String uid, BigDecimal initialBalance) {
		MifareUid.Parsed parsed = requireParsedUid(uid);
		if (cardRepository.findFirstByUidIn(parsed.lookupKeys()).isPresent()) {
			throw new DuplicateCardUidException(parsed.canonical());
		}
		BigDecimal bal = Money.normalize(initialBalance != null ? initialBalance : BigDecimal.ZERO);
		Card card = new Card(parsed.canonical(), bal);
		cardRepository.save(card);
		if (bal.compareTo(BigDecimal.ZERO) > 0) {
			ledgerEntryRepository.save(
					new CardLedgerEntry(card, TransactionType.LOAD_CASH, bal, bal, MSG_FIRST_LOAD));
		}
		log.info("Kart tanimlandi: uidHint={}", MifareUid.mask(parsed.canonical()));
		return card;
	}

	@Transactional(readOnly = true)
	public Card getByUid(String uid) {
		String key = uid != null ? uid.trim() : "";
		return findCardByUidFlexible(key).orElseThrow(() -> new CardNotFoundException(key));
	}

	@Transactional(readOnly = true)
	public CardStatus getStatus(String uid) {
		return getByUid(uid).getStatus();
	}

	@Transactional
	public CardDetailResponse getCardDetail(String uid) {
		MifareUid.Parsed parsed = requireParsedUid(uid);
		Card card = findCardByUidFlexible(parsed.canonical()).orElse(null);
		if (card == null) {
			card = cardRepository.findByUid(parsed.canonical()).orElse(null);
		}
		if (card == null) {
			card = cardRepository.save(new Card(parsed.canonical(), BigDecimal.ZERO));
			log.info("Kart detay / ilk kayit: uidHint={}", MifareUid.mask(parsed.canonical()));
		}
		List<CardLedgerEntry> entries = ledgerEntriesForInquiryToday(card.getUid());
		List<LedgerEntryResponse> ledger = entries.stream().map(LedgerEntryResponse::from).toList();
		String lockedPaymentMethod = resolveBalanceLoadPaymentMethodForCard(card).orElse(null);
		return CardDetailResponse.build(card, entries, ledger, lockedPaymentMethod);
	}

	/**
	 * POS kart sorgulama: yalnızca İstanbul takvim günündeki hareketler.
	 * Geçmiş satış kayıtları silinmez; raporlar tüm {@code card_ledger} üzerinden çalışır.
	 */
	private List<CardLedgerEntry> ledgerEntriesForInquiryToday(String uid) {
		LocalDate today = LocalDate.now(PARK_ZONE);
		Instant from = today.atStartOfDay(PARK_ZONE).toInstant();
		Instant to = today.plusDays(1).atStartOfDay(PARK_ZONE).toInstant();
		return ledgerEntryRepository.findByCardUidAndCreatedAtRange(uid, from, to);
	}

	public static final String INQUIRY_REFUND_DESC_PREFIX = "POS sorgulama — nakit iade";
	public static final String INQUIRY_FORFEIT_DESC_PREFIX = "POS sorgulama — bakiye sifirlama";

	@Transactional
	public CashRefundResponse cashRefundAtInquiry(String uid, String operatorUserId, BigDecimal requestedAmount) {
		MifareUid.Parsed parsed = requireParsedUid(uid);
		Card card = cardRepository.findFirstByUidInForUpdate(parsed.lookupKeys())
				.orElseThrow(() -> new CardNotFoundException(parsed.canonical()));
		if (card.getStatus() != CardStatus.ACTIVE) {
			throw new CardBlockedException(parsed.canonical());
		}
		BigDecimal balance = Money.normalize(card.getBalance());
		if (balance.compareTo(BigDecimal.ZERO) <= 0) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Kart bakiyesi zaten sıfır");
		}
		BigDecimal req = Money.normalize(requestedAmount != null ? requestedAmount : BigDecimal.ZERO);
		if (req.compareTo(BigDecimal.ZERO) < 0) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "İade tutarı negatif olamaz");
		}
		List<CardLedgerEntry> entries = ledgerEntriesForInquiryToday(card.getUid());
		BigDecimal cashRefundableMax = CardDetailResponse.build(card, entries, List.of(), null).cashRefundableAmount();
		if (cashRefundableMax.compareTo(balance) > 0) {
			cashRefundableMax = balance;
		}
		BigDecimal refundCash = req.min(cashRefundableMax);
		BigDecimal forfeit = Money.normalize(balance.subtract(refundCash));
		BigDecimal running = balance;
		String kasiyer = operatorUserId != null && !operatorUserId.isBlank() ? operatorUserId.trim() : "—";
		if (refundCash.compareTo(BigDecimal.ZERO) > 0) {
			running = Money.normalize(running.subtract(refundCash));
			String desc = String.format(
					"%s · Kasiyer: %s · Odenen: %s · Kalan bakiye: %s",
					INQUIRY_REFUND_DESC_PREFIX,
					kasiyer,
					Money.formatTryLabel(refundCash),
					Money.formatTryLabel(running));
			ledgerEntryRepository.save(new CardLedgerEntry(
					card, TransactionType.REFUND_CASH, refundCash.negate(), running, desc));
		}
		if (forfeit.compareTo(BigDecimal.ZERO) > 0) {
			running = BigDecimal.ZERO;
			String desc = String.format(
					"%s · Kasiyer: %s · Iade edilemez tutar: %s",
					INQUIRY_FORFEIT_DESC_PREFIX,
					kasiyer,
					Money.formatTryLabel(forfeit));
			ledgerEntryRepository.save(new CardLedgerEntry(
					card, TransactionType.DAILY_RESET, forfeit.negate(), running, desc));
		}
		card.setBalance(BigDecimal.ZERO);
		Card saved = cardRepository.save(card);
		log.info(
				"Kart sorgulama iadesi: uidHint={}, kasiyer={}, nakitIade={}, sifirlanan={}, bakiyeSonra={}",
				MifareUid.mask(parsed.canonical()),
				kasiyer,
				refundCash,
				forfeit,
				saved.getBalance());
		return new CashRefundResponse(saved.getUid(), saved.getBalance(), refundCash, forfeit);
	}

	@Transactional
	public Card grantTicketEntry(String uid, String operatorUserId, String paymentMethod, BigDecimal saleAmount,
			List<TicketGrantLineRequest> lines) {
		MifareUid.Parsed parsed = requireParsedUid(uid);
		BigDecimal amt = Money.normalize(saleAmount);
		if (amt.compareTo(BigDecimal.ZERO) < 0) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tutar negatif olamaz");
		}
		if (amt.compareTo(new BigDecimal("999999.99")) > 0) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tutar üst sınırı aşıldı");
		}
		String pm = paymentMethod != null ? paymentMethod.trim().toLowerCase() : "";
		if (amt.compareTo(BigDecimal.ZERO) == 0 && !"credit".equals(pm)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ücretsiz acenta biletleri için ödeme: credit");
		}
		TransactionType txType = switch (pm) {
			case "card" -> TransactionType.TICKET_CARD;
			case "credit" -> TransactionType.TICKET_CREDIT;
			case "cash" -> TransactionType.TICKET_CASH;
			default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ödeme: cash, card veya credit");
		};
		if (amt.compareTo(BigDecimal.ZERO) == 0 && txType != TransactionType.TICKET_CREDIT) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ücretsiz biletler acenta (credit) olarak kaydedilir");
		}
		boolean newCard = false;
		Card card = findCardByUidFlexible(parsed.canonical())
				.map(c -> cardRepository.findByUidForUpdate(c.getUid()).orElseThrow())
				.orElse(null);
		if (card == null) {
			card = cardRepository.findByUidForUpdate(parsed.canonical()).orElse(null);
		}
		if (card == null) {
			card = cardRepository.save(new Card(parsed.canonical(), BigDecimal.ZERO));
			newCard = true;
		}
		if (card.getStatus() != CardStatus.ACTIVE) {
			throw new CardBlockedException(parsed.canonical());
		}
		/* Ledger silinmediği için yalnızca bugünkü (İstanbul) bilet kaydı engeller — ertesi gün aynı kart OK */
		LocalDate ticketDay = LocalDate.now(PARK_ZONE);
		Instant ticketDayFrom = ticketDay.atStartOfDay(PARK_ZONE).toInstant();
		Instant ticketDayTo = ticketDay.plusDays(1).atStartOfDay(PARK_ZONE).toInstant();
		TicketGrantPolicy.assertNoPriorTicketLoad(ledgerEntryRepository.existsByCard_IdAndTypeInAndCreatedAtRange(
				card.getId(), TicketGrantPolicy.TICKET_TYPES, ticketDayFrom, ticketDayTo));
		TicketGrantPolicy.assertTicketGrantAllowed(card, lines, amt.compareTo(BigDecimal.ZERO) > 0);
		card.setEntryGate(1);
		Card saved = cardRepository.save(card);
		BigDecimal bal = Money.normalize(saved.getBalance());
		if (amt.compareTo(BigDecimal.ZERO) > 0) {
			String payKind = switch (pm) {
				case "card" -> "kredi karti";
				case "credit" -> "kredili";
				default -> "nakit";
			};
			String desc = String.format(
					"POS bilet — %s odeme · Tahsilat: %s · Turnike giris hakki (entryGate=1)",
					payKind,
					Money.formatTryLabel(amt));
			ledgerEntryRepository.save(new CardLedgerEntry(saved, txType, amt, bal, desc));
		}
		if (lines != null) {
			for (TicketGrantLineRequest line : lines) {
				if (line == null || line.ticketAgeGroupId() == null) {
					continue;
				}
				TicketAgeGroup tag = ticketAgeGroupRepository.findById(line.ticketAgeGroupId()).orElse(null);
				if (tag == null) {
					continue;
				}
				int qty = Math.max(1, line.quantity());
				if (tag.isAgencyComplimentary()) {
					String agencyDesc = String.format(
							"POS acenta bilet — %s × %d · Turnike giris hakki (entryGate=1)",
							tag.getName(),
							qty);
					ledgerEntryRepository.save(new CardLedgerEntry(
							saved, TransactionType.TICKET_CREDIT, BigDecimal.ZERO, bal, null, null, tag, qty, agencyDesc));
					continue;
				}
				if (txType == TransactionType.TICKET_CREDIT) {
					continue;
				}
				String lineDesc = String.format(
						"POS bilet — %s × %d · Turnike giris hakki (entryGate=1)",
						tag.getName(),
						qty);
				ledgerEntryRepository.save(new CardLedgerEntry(
						saved, txType, BigDecimal.ZERO, bal, null, null, tag, qty, lineDesc));
			}
		}
		String kasiyer = operatorUserId != null && !operatorUserId.isBlank() ? operatorUserId.trim() : "—";
		log.info(
				"Kart tanimlama (bilet): uidHint={}, kasiyer={}, yeniKart={}, entryGate={}, bakiye={}, odeme={}, tutar={}, satirSayisi={}",
				MifareUid.mask(parsed.canonical()),
				kasiyer,
				newCard,
				saved.getEntryGate(),
				saved.getBalance(),
				pm,
				amt,
				lines != null ? lines.size() : 0);
		return saved;
	}

	@Transactional
	public Card loadBalance(String uid, BigDecimal amount, String paymentMethod, String operatorUserId) {
		MifareUid.Parsed parsed = requireParsedUid(uid);
		BigDecimal amt = Money.normalize(amount);
		if (amt.compareTo(BigDecimal.ZERO) <= 0) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tutar pozitif olmalıdır");
		}
		if (amt.compareTo(new BigDecimal("999999.99")) > 0) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tutar üst sınırı aşıldı");
		}
		String pm = paymentMethod != null ? paymentMethod.trim().toLowerCase() : "";
		TransactionType txType = switch (pm) {
			case "card" -> TransactionType.LOAD_CARD;
			case "rate" -> TransactionType.LOAD_AGENCY;
			case "cash" -> TransactionType.LOAD_CASH;
			default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ödeme: cash, card veya rate");
		};
		Card card = findCardByUidFlexible(parsed.canonical())
				.map(c -> cardRepository.findByUidForUpdate(c.getUid()).orElseThrow())
				.orElse(null);
		if (card == null) {
			card = cardRepository.findByUidForUpdate(parsed.canonical()).orElse(null);
		}
		if (card == null) {
			card = cardRepository.save(new Card(parsed.canonical(), BigDecimal.ZERO));
		}
		if (card.getStatus() != CardStatus.ACTIVE) {
			throw new CardBlockedException(parsed.canonical());
		}
		ledgerEntryRepository
				.findFirstByCard_IdAndTypeInOrderByCreatedAtAscIdAsc(card.getId(), BALANCE_LOAD_TYPES)
				.ifPresent(first -> BalanceLoadPaymentPolicy.assertBalanceLoadPaymentAllowed(pm, first.getType()));
		BigDecimal before = Money.normalize(card.getBalance());
		BigDecimal after = Money.normalize(before.add(amt));
		String payKind = switch (pm) {
			case "card" -> "kredi karti";
			case "rate" -> "nakit kuru";
			default -> "nakit";
		};
		String desc = String.format(
				"POS — %s ile bakiye yukleme · Yuklenen: %s · Yeni bakiye: %s",
				payKind,
				Money.formatTryLabel(amt),
				Money.formatTryLabel(after));
		card.setBalance(after);
		ledgerEntryRepository.save(new CardLedgerEntry(card, txType, amt, after, desc));
		Card saved = cardRepository.save(card);
		String kasiyer = operatorUserId != null && !operatorUserId.isBlank() ? operatorUserId.trim() : "—";
		log.info(
				"Bakiye yukleme: uidHint={}, kasiyer={}, tutar={}, odeme={}, bakiyeOnce={}, bakiyeSonra={}",
				MifareUid.mask(parsed.canonical()),
				kasiyer,
				amt,
				pm,
				before,
				saved.getBalance());
		return saved;
	}

	@Transactional
	public void ensureDemoCard(String uid, BigDecimal targetBalance) {
		String key = uid != null ? uid.trim() : "";
		if (key.isEmpty()) {
			return;
		}
		BigDecimal target = Money.normalize(targetBalance != null ? targetBalance : BigDecimal.ZERO);
		if (!cardRepository.existsByUid(key)) {
			issueCard(key, target.compareTo(BigDecimal.ZERO) == 0 ? BigDecimal.ZERO : target);
			return;
		}
		Card card = cardRepository.findByUid(key).orElseThrow(() -> new CardNotFoundException(key));
		if (Money.normalize(card.getBalance()).compareTo(target) != 0) {
			card.setBalance(target);
			cardRepository.save(card);
		}
	}
}
