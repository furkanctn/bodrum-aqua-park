package com.bodrumaquapark.service;

import java.math.BigDecimal;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bodrumaquapark.config.EntryProperties;
import com.bodrumaquapark.entity.Card;
import com.bodrumaquapark.entity.CardLedgerEntry;
import com.bodrumaquapark.entity.CardStatus;
import com.bodrumaquapark.entity.TransactionType;
import com.bodrumaquapark.exception.CardNotFoundException;
import com.bodrumaquapark.repository.CardLedgerEntryRepository;
import com.bodrumaquapark.util.Money;

@Service
public class TurnstileService {

	private final CardLedgerEntryRepository ledgerEntryRepository;
	private final EntryProperties entryProperties;
	private final CardService cardService;

	public TurnstileService(CardLedgerEntryRepository ledgerEntryRepository,
			EntryProperties entryProperties, CardService cardService) {
		this.ledgerEntryRepository = ledgerEntryRepository;
		this.entryProperties = entryProperties;
		this.cardService = cardService;
	}

	@Transactional
	public TurnstileScanResult scan(String cardUid) {
		String uid = cardUid != null ? cardUid.trim() : "";
		if (uid.isEmpty()) {
			return TurnstileScanResult.notFound();
		}
		try {
			return process(cardService.findCardForUpdateByUidFlexible(uid));
		} catch (CardNotFoundException e) {
			return TurnstileScanResult.notFound();
		}
	}

	private TurnstileScanResult process(Card card) {
		if (card.getStatus() != CardStatus.ACTIVE) {
			return TurnstileScanResult.blocked();
		}
		/* Bilet ile verilen tek geçiş: entry_gate=1 ise bakiye düşmeden geçiş, sonra 0 */
		if (card.getEntryGate() == 1) {
			card.setEntryGate(0);
			BigDecimal balance = Money.normalize(card.getBalance());
			ledgerEntryRepository.save(new CardLedgerEntry(card, TransactionType.ENTRY, BigDecimal.ZERO, balance, null,
					"Turnike — bilet girişi"));
			return TurnstileScanResult.allowed(balance, BigDecimal.ZERO);
		}
		BigDecimal fee = Money.normalize(entryProperties.getFee());
		BigDecimal balance = Money.normalize(card.getBalance());

		if (fee.compareTo(BigDecimal.ZERO) == 0) {
			if (balance.compareTo(BigDecimal.ZERO) <= 0) {
				return TurnstileScanResult.insufficient(balance, BigDecimal.ZERO);
			}
			ledgerEntryRepository.save(new CardLedgerEntry(card, TransactionType.ENTRY, BigDecimal.ZERO, balance, null,
					"Turnike geçişi (ücretsiz)"));
			return TurnstileScanResult.allowed(balance, BigDecimal.ZERO);
		}

		if (balance.compareTo(fee) < 0) {
			return TurnstileScanResult.insufficient(balance, fee);
		}

		BigDecimal newBalance = Money.normalize(balance.subtract(fee));
		card.setBalance(newBalance);
		BigDecimal amountChange = fee.negate();
		ledgerEntryRepository.save(new CardLedgerEntry(card, TransactionType.ENTRY, amountChange, newBalance, null,
				"Turnike giriş ücreti"));
		return TurnstileScanResult.allowed(newBalance, fee);
	}
}
