package com.bodrumaquapark.service;

import java.math.BigDecimal;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.bodrumaquapark.entity.Card;
import com.bodrumaquapark.entity.CardLedgerEntry;
import com.bodrumaquapark.entity.CardStatus;
import com.bodrumaquapark.entity.Product;
import com.bodrumaquapark.entity.TransactionType;
import com.bodrumaquapark.exception.CardBlockedException;
import com.bodrumaquapark.exception.InsufficientBalanceException;
import com.bodrumaquapark.exception.OutOfStockException;
import com.bodrumaquapark.exception.ProductNotFoundException;
import com.bodrumaquapark.repository.CardLedgerEntryRepository;
import com.bodrumaquapark.repository.ProductRepository;
import com.bodrumaquapark.util.Money;

@Service
public class SaleService {

	private final ProductRepository productRepository;
	private final CardLedgerEntryRepository ledgerEntryRepository;
	private final CardService cardService;

	public SaleService(ProductRepository productRepository,
			CardLedgerEntryRepository ledgerEntryRepository, CardService cardService) {
		this.productRepository = productRepository;
		this.ledgerEntryRepository = ledgerEntryRepository;
		this.cardService = cardService;
	}

	@Transactional
	public SaleResult sell(String cardUid, Long productId, Set<String> allowedSaleAreaCodes) {
		String uid = cardUid != null ? cardUid.trim() : "";
		Card card = cardService.findCardForUpdateByUidFlexible(uid);
		if (card.getStatus() != CardStatus.ACTIVE) {
			throw new CardBlockedException(uid);
		}
		Product product = productRepository.findByIdForUpdate(productId)
				.orElseThrow(() -> new ProductNotFoundException(productId));
		if (!product.isActive()) {
			throw new ProductNotFoundException(productId);
		}
		String areaCode = product.getSaleArea().getCode();
		if (allowedSaleAreaCodes == null || allowedSaleAreaCodes.isEmpty()
				|| !allowedSaleAreaCodes.contains(areaCode)) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Bu ürünü satma yetkiniz yok");
		}

		if (product.getStockQuantity() != null && product.getStockQuantity() <= 0) {
			throw new OutOfStockException(product.getName());
		}

		BigDecimal price = Money.normalize(product.getPrice());
		BigDecimal balance = Money.normalize(card.getBalance());
		if (balance.compareTo(price) < 0) {
			throw new InsufficientBalanceException(balance, price);
		}

		if (product.getStockQuantity() != null) {
			product.setStockQuantity(product.getStockQuantity() - 1);
		}

		BigDecimal newBalance = Money.normalize(balance.subtract(price));
		card.setBalance(newBalance);

		String areaName = product.getSaleArea().getName();
		String desc = String.format(
				"Harcama · İstasyon: %s — %s · Harcanan: %s · Kalan bakiye: %s",
				areaName,
				product.getName(),
				Money.formatTryLabel(price),
				Money.formatTryLabel(newBalance));
		ledgerEntryRepository.save(new CardLedgerEntry(card, TransactionType.SALE, price.negate(), newBalance, product,
				desc));

		return new SaleResult(newBalance, product.getName(), product.getSaleArea().getCode(),
				product.getSaleArea().getName(), price);
	}
}
