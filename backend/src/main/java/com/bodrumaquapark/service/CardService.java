package com.bodrumaquapark.service;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
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
import com.bodrumaquapark.entity.TransactionType;
import com.bodrumaquapark.exception.CardBlockedException;
import com.bodrumaquapark.exception.CardNotFoundException;
import com.bodrumaquapark.exception.DuplicateCardUidException;
import com.bodrumaquapark.repository.CardLedgerEntryRepository;
import com.bodrumaquapark.repository.CardRepository;
import com.bodrumaquapark.util.Money;
import com.bodrumaquapark.web.dto.CardDetailResponse;
import com.bodrumaquapark.web.dto.LedgerEntryResponse;

@Service
public class CardService {

	private static final Logger log = LoggerFactory.getLogger(CardService.class);

	private static final String MSG_FIRST_LOAD = "Ilk bakiye yukleme";

	private final CardRepository cardRepository;
	private final CardLedgerEntryRepository ledgerEntryRepository;
	private final CardProperties cardProperties;

	public CardService(CardRepository cardRepository, CardLedgerEntryRepository ledgerEntryRepository,
			CardProperties cardProperties) {
		this.cardRepository = cardRepository;
		this.ledgerEntryRepository = ledgerEntryRepository;
		this.cardProperties = cardProperties;
	}

	/**
	 * Aynı fiziksel kart için: önde sıfır (00137…), düz (137…), ham bayt hex (A1B2C3D4) ile
	 * veritabanında ondalık saklanan UID eşleşebilir — hepsi aynı sayısal değere indirgenir.
	 */
	private Optional<Card> findCardByUidFlexible(String raw) {
		String key = raw != null ? raw.trim() : "";
		if (key.isEmpty()) {
			return Optional.empty();
		}
		Optional<Card> o = cardRepository.findByUid(key);
		if (o.isPresent()) {
			return o;
		}
		Optional<BigInteger> wanted = parseUidToBigInteger(key);
		if (wanted.isEmpty()) {
			return Optional.empty();
		}
		BigInteger bi = wanted.get();
		String dec = bi.toString();
		o = cardRepository.findByUid(dec);
		if (o.isPresent()) {
			return o;
		}
		String hexU = bi.toString(16).toUpperCase(Locale.ROOT);
		o = cardRepository.findByUid(hexU);
		if (o.isPresent()) {
			return o;
		}
		o = cardRepository.findByUid(hexU.toLowerCase(Locale.ROOT));
		if (o.isPresent()) {
			return o;
		}
		return cardRepository.findAll().stream()
				.filter(c -> parseUidToBigInteger(c.getUid()).map(bi::equals).orElse(false))
				.findFirst();
	}

	private static boolean isHexUidString(String t) {
		if (t.length() < 4) {
			return false;
		}
		for (int i = 0; i < t.length(); i++) {
			char c = t.charAt(i);
			if (!((c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F'))) {
				return false;
			}
		}
		return true;
	}

	/** Ondalık rakam dizisi veya hex (RFID ham bayt) → aynı BigInteger. */
	private static Optional<BigInteger> parseUidToBigInteger(String s) {
		if (s == null || s.isBlank()) {
			return Optional.empty();
		}
		String t = s.trim();
		if (t.chars().allMatch(Character::isDigit)) {
			try {
				return Optional.of(new BigInteger(t));
			} catch (Exception e) {
				return Optional.empty();
			}
		}
		if (isHexUidString(t)) {
			try {
				return Optional.of(new BigInteger(t, 16));
			} catch (Exception e) {
				return Optional.empty();
			}
		}
		return Optional.empty();
	}

	/**
	 * Yeni kart satırı için UID: mümkünse sayısal kanonik (hex okuma → ondalık string), böylece DB’deki
	 * mevcut satırla çakışma ve “yükleme başka uid’ye yazıldı” durumu azalır.
	 */
	private String canonicalUidForNewCard(String key) {
		return parseUidToBigInteger(key).map(BigInteger::toString).orElse(key);
	}

	/**
	 * Satış / turnike: istekteki UID ile esnek eşleşme, kilit için DB’deki kanonik uid kullanılır.
	 */
	@Transactional
	public Card findCardForUpdateByUidFlexible(String rawUid) {
		String key = rawUid != null ? rawUid.trim() : "";
		if (key.isEmpty()) {
			throw new CardNotFoundException(key);
		}
		return findCardByUidFlexible(key)
				.map(c -> cardRepository.findByUidForUpdate(c.getUid()).orElseThrow(() -> new CardNotFoundException(key)))
				.orElseThrow(() -> new CardNotFoundException(key));
	}

	@Transactional
	public Card issueCard(String uid, BigDecimal initialBalance) {
		String key = uid != null ? uid.trim() : "";
		if (cardRepository.existsByUid(key)) {
			throw new DuplicateCardUidException(key);
		}
		BigDecimal requested = Money.normalize(initialBalance != null ? initialBalance : BigDecimal.ZERO);
		BigDecimal bal;
		if (requested.compareTo(BigDecimal.ZERO) == 0) {
			bal = Money.normalize(cardProperties.getDefaultInitialBalance());
		} else {
			bal = requested;
		}
		Card card = new Card(key, bal);
		cardRepository.save(card);
		if (bal.compareTo(BigDecimal.ZERO) > 0) {
			ledgerEntryRepository.save(
					new CardLedgerEntry(card, TransactionType.LOAD_CASH, bal, bal, null, MSG_FIRST_LOAD));
		}
		return card;
	}

	@Transactional(readOnly = true)
	public Card getByUid(String uid) {
		String key = uid != null ? uid.trim() : "";
		return cardRepository.findByUid(key).orElseThrow(() -> new CardNotFoundException(key));
	}

	@Transactional(readOnly = true)
	public CardStatus getStatus(String uid) {
		return getByUid(uid).getStatus();
	}

	/**
	 * POS kart özeti + defter. Veritabanında yoksa (ilk fiziksel okuma) sıfır bakiyeli kart kaydı açılır;
	 * bakiye yükleme ve bilet tanımlamadaki davranışla uyumludur.
	 */
	@Transactional
	public CardDetailResponse getCardDetail(String uid) {
		String key = uid != null ? uid.trim() : "";
		if (key.isEmpty()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Kart UID gerekli");
		}
		Card card = findCardByUidFlexible(key).orElse(null);
		if (card == null) {
			String canon = canonicalUidForNewCard(key);
			card = cardRepository.findByUid(canon).orElse(null);
		}
		if (card == null) {
			String newUid = canonicalUidForNewCard(key);
			card = cardRepository.save(new Card(newUid, BigDecimal.ZERO));
			log.info("Kart detay / ilk kayit: uid={}", card.getUid());
		}
		// Defter satırları card_id ile bağlı; sorgu istemdeki key ile değil, DB’deki uid ile çekilmeli
		// (ör. yükleme 00137… ile, sorgu 137… ile gelince aynı kart bulunur ve hareketler gelir).
		List<CardLedgerEntry> entries = ledgerEntryRepository.findByCard_UidOrderByCreatedAtDesc(card.getUid());
		List<LedgerEntryResponse> ledger = new ArrayList<>(entries.size());
		for (CardLedgerEntry e : entries) {
			ledger.add(LedgerEntryResponse.from(e));
		}
		return CardDetailResponse.build(card, entries, ledger);
	}

	/**
	 * POS bilet satışı: turnike giriş hakkı + tahsilat satırı (nakit / kart / kredi ayrımı raporlarda).
	 */
	@Transactional
	public Card grantTicketEntry(String uid, String operatorUserId, String paymentMethod, BigDecimal saleAmount) {
		String key = uid != null ? uid.trim() : "";
		if (key.isEmpty()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Kart UID gerekli");
		}
		BigDecimal amt = Money.normalize(saleAmount);
		if (amt.compareTo(BigDecimal.ZERO) <= 0) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tutar pozitif olmalıdır");
		}
		if (amt.compareTo(new BigDecimal("999999.99")) > 0) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tutar üst sınırı aşıldı");
		}
		String pm = paymentMethod != null ? paymentMethod.trim().toLowerCase() : "";
		TransactionType txType = switch (pm) {
			case "card" -> TransactionType.TICKET_CARD;
			case "credit" -> TransactionType.TICKET_CREDIT;
			case "cash" -> TransactionType.TICKET_CASH;
			default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ödeme: cash, card veya credit");
		};
		boolean newCard = false;
		Card card = findCardByUidFlexible(key)
				.map(c -> cardRepository.findByUidForUpdate(c.getUid()).orElseThrow())
				.orElse(null);
		if (card == null) {
			String canon = canonicalUidForNewCard(key);
			card = cardRepository.findByUidForUpdate(canon).orElse(null);
		}
		if (card == null) {
			card = cardRepository.save(new Card(canonicalUidForNewCard(key), BigDecimal.ZERO));
			newCard = true;
		}
		if (card.getStatus() != CardStatus.ACTIVE) {
			throw new CardBlockedException(key);
		}
		card.setEntryGate(1);
		Card saved = cardRepository.save(card);
		BigDecimal bal = Money.normalize(saved.getBalance());
		String payKind = switch (pm) {
			case "card" -> "kredi karti";
			case "credit" -> "kredili";
			default -> "nakit";
		};
		String desc = String.format(
				"POS bilet — %s odeme · Tahsilat: %s · Turnike giris hakki (entryGate=1)",
				payKind,
				Money.formatTryLabel(amt));
		ledgerEntryRepository.save(new CardLedgerEntry(saved, txType, amt, bal, null, desc));
		String kasiyer = operatorUserId != null && !operatorUserId.isBlank() ? operatorUserId.trim() : "—";
		log.info(
				"Kart tanımlama (bilet satışı / turnike giriş hakkı): uid={}, kasiyer={}, yeniKart={}, entryGate={}, bakiye={}, odeme={}, tutar={}",
				key,
				kasiyer,
				newCard,
				saved.getEntryGate(),
				saved.getBalance(),
				pm,
				amt);
		return saved;
	}

	/**
	 * POS bakiye yükleme — deftere yüklenir, bakiye artar.
	 */
	@Transactional
	public Card loadBalance(String uid, BigDecimal amount, String paymentMethod, String operatorUserId) {
		String key = uid != null ? uid.trim() : "";
		if (key.isEmpty()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Kart UID gerekli");
		}
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
		Card card = findCardByUidFlexible(key)
				.map(c -> cardRepository.findByUidForUpdate(c.getUid()).orElseThrow())
				.orElse(null);
		if (card == null) {
			String canon = canonicalUidForNewCard(key);
			card = cardRepository.findByUidForUpdate(canon).orElse(null);
		}
		if (card == null) {
			card = cardRepository.save(new Card(canonicalUidForNewCard(key), BigDecimal.ZERO));
		}
		if (card.getStatus() != CardStatus.ACTIVE) {
			throw new CardBlockedException(key);
		}
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
		ledgerEntryRepository.save(new CardLedgerEntry(card, txType, amt, after, null, desc));
		Card saved = cardRepository.save(card);
		String kasiyer = operatorUserId != null && !operatorUserId.isBlank() ? operatorUserId.trim() : "—";
		log.info(
				"Bakiye yukleme: uid={}, kasiyer={}, tutar={}, odeme={}, bakiyeOnce={}, bakiyeSonra={}",
				key,
				kasiyer,
				amt,
				pm,
				before,
				saved.getBalance());
		return saved;
	}

	/**
	 * Demo / geliştirme: belirtilen UID yoksa verilen bakiye ile kart açılır; varsa bakiye hedef tutara çekilir (deftere ek satır yok).
	 */
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
