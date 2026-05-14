package com.bodrumaquapark.service;

import java.math.BigDecimal;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.bodrumaquapark.config.PrinterProperties;
import com.bodrumaquapark.entity.Card;
import com.bodrumaquapark.entity.CardLedgerEntry;
import com.bodrumaquapark.entity.CardStatus;
import com.bodrumaquapark.entity.Product;
import com.bodrumaquapark.entity.TransactionType;
import com.bodrumaquapark.exception.CardBlockedException;
import com.bodrumaquapark.exception.InsufficientBalanceException;
import com.bodrumaquapark.exception.OutOfStockException;
import com.bodrumaquapark.exception.PrinterNotAvailableException;
import com.bodrumaquapark.exception.ProductNotFoundException;
import com.bodrumaquapark.repository.CardLedgerEntryRepository;
import com.bodrumaquapark.repository.ProductRepository;
import com.bodrumaquapark.util.Money;

@Service
public class SaleService {

	private static final Logger log = LoggerFactory.getLogger(SaleService.class);

	private final ProductRepository productRepository;
	private final CardLedgerEntryRepository ledgerEntryRepository;
	private final CardService cardService;
	private final PrinterService printerService;
	private final PrinterProperties printerProperties;

	public SaleService(ProductRepository productRepository,
			CardLedgerEntryRepository ledgerEntryRepository, CardService cardService,
			PrinterService printerService, PrinterProperties printerProperties) {
		this.productRepository = productRepository;
		this.ledgerEntryRepository = ledgerEntryRepository;
		this.cardService = cardService;
		this.printerService = printerService;
		this.printerProperties = printerProperties;
	}

	@Transactional
	public SaleResult sell(String cardUid, Long productId, Set<String> allowedSaleAreaCodes) {
		// Yazıcı zorunluysa önce kontrol et - bakiye düşmeden hata ver
		if (printerProperties.isRequiredForSale()) {
			if (!printerService.isPrinterAvailable()) {
				String target = printerService.effectivePort(null);
				if (target == null) {
					target = printerService.effectiveWindowsQueueName().orElse("tanımlı değil");
				}
				log.warn("Satış reddedildi: Fiş yazıcı bağlı değil (hedef: {})", target);
				throw new PrinterNotAvailableException(target);
			}
		}

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
