package com.bodrumaquapark;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.bodrumaquapark.entity.Card;
import com.bodrumaquapark.entity.CardLedgerEntry;
import com.bodrumaquapark.entity.TransactionType;
import com.bodrumaquapark.repository.CardLedgerEntryRepository;
import com.bodrumaquapark.repository.CardRepository;
import com.bodrumaquapark.service.CardService;
import com.bodrumaquapark.web.dto.CardDetailResponse;

import jakarta.persistence.EntityManager;

/**
 * Ledger silinmeden ertesi gün kartın "sıfır kart" gibi davranması.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class CardDayBoundaryIntegrationTest {

	@Autowired
	private CardService cardService;

	@Autowired
	private CardRepository cardRepository;

	@Autowired
	private CardLedgerEntryRepository ledgerEntryRepository;

	@Autowired
	private EntityManager entityManager;

	@Test
	void yesterdayTicket_doesNotBlockTodayTicketGrant() {
		String uid = "04AA0001";
		Card card = cardRepository.save(new Card(uid, BigDecimal.ZERO));
		ledgerEntryRepository.save(new CardLedgerEntry(
				card, TransactionType.TICKET_CASH, new BigDecimal("100.00"), BigDecimal.ZERO, "dun bilet"));
		backdateLatestLedger(card.getId(), daysAgoStart(2));
		card.setEntryGate(0);
		cardRepository.save(card);
		entityManager.flush();

		Card granted = cardService.grantTicketEntry(uid, "0000", "cash", new BigDecimal("50.00"), List.of());
		assertThat(granted.getEntryGate()).isEqualTo(1);
	}

	@Test
	void secondTicketSameDay_isRejected() {
		String uid = "04AA0002";
		cardService.grantTicketEntry(uid, "0000", "cash", new BigDecimal("50.00"), List.of());

		assertThatThrownBy(() -> cardService.grantTicketEntry(uid, "0000", "cash", new BigDecimal("50.00"), List.of()))
				.isInstanceOf(ResponseStatusException.class)
				.hasMessageContaining("bugün zaten bilet");
	}

	@Test
	void yesterdayCashLoad_doesNotLockTodayCardPayment() {
		String uid = "04AA0003";
		Card card = cardRepository.save(new Card(uid, BigDecimal.ZERO));
		ledgerEntryRepository.save(new CardLedgerEntry(
				card, TransactionType.LOAD_CASH, new BigDecimal("100.00"), new BigDecimal("100.00"), "dun nakit"));
		backdateLatestLedger(card.getId(), daysAgoStart(1));
		card.setBalance(BigDecimal.ZERO);
		cardRepository.save(card);
		entityManager.flush();

		Card loaded = cardService.loadBalance(uid, new BigDecimal("80.00"), "card", "0000");
		assertThat(loaded.getBalance()).isEqualByComparingTo("80.00");
	}

	@Test
	void cardInquiry_showsOnlyTodayLedger() {
		String uid = "04AA0004";
		Card card = cardRepository.save(new Card(uid, BigDecimal.ZERO));
		ledgerEntryRepository.save(new CardLedgerEntry(
				card, TransactionType.LOAD_CASH, new BigDecimal("100.00"), new BigDecimal("100.00"), "dun"));
		backdateLatestLedger(card.getId(), daysAgoStart(1));
		ledgerEntryRepository.save(new CardLedgerEntry(
				card, TransactionType.LOAD_CASH, new BigDecimal("20.00"), new BigDecimal("20.00"), "bugun"));
		card.setBalance(new BigDecimal("20.00"));
		cardRepository.save(card);
		entityManager.flush();
		entityManager.clear();

		CardDetailResponse detail = cardService.getCardDetail(uid);
		assertThat(detail.ledger()).hasSize(1);
		assertThat(detail.ledger().get(0).description()).contains("bugun");
		assertThat(detail.totalLoaded()).isEqualByComparingTo("20.00");
	}

	@Test
	void colonSeparatedUid_parsesWhenStrict() {
		assertThat(com.bodrumaquapark.util.MifareUid.parse("04:BB:CC:DD", true, true)).isPresent();
	}

	private Instant daysAgoStart(int daysAgo) {
		LocalDate day = LocalDate.now(CardService.PARK_ZONE).minusDays(daysAgo);
		return day.atStartOfDay(CardService.PARK_ZONE).plusHours(12).toInstant();
	}

	private void backdateLatestLedger(Long cardId, Instant when) {
		entityManager.createNativeQuery(
				"UPDATE card_ledger SET created_at = ?1 WHERE id = ("
						+ "SELECT id FROM card_ledger WHERE card_id = ?2 ORDER BY id DESC LIMIT 1)")
				.setParameter(1, java.sql.Timestamp.from(when))
				.setParameter(2, cardId)
				.executeUpdate();
		entityManager.flush();
		entityManager.clear();
	}
}
