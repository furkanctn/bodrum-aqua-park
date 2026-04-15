package com.bodrumaquapark.entity;

import java.math.BigDecimal;
import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

@Entity
@Table(name = "card_ledger")
public class CardLedgerEntry {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "card_id", nullable = false)
	private Card card;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 32)
	private TransactionType type;

	/** Pozitif: yükleme; negatif: düşüm */
	@Column(nullable = false, precision = 12, scale = 2)
	private BigDecimal amountChange;

	@Column(nullable = false, precision = 12, scale = 2)
	private BigDecimal balanceAfter;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "product_id")
	private Product product;

	@Column(length = 512)
	private String description;

	@Column(nullable = false, updatable = false)
	private Instant createdAt;

	@PrePersist
	void onCreate() {
		createdAt = Instant.now();
	}

	protected CardLedgerEntry() {
	}

	public CardLedgerEntry(Card card, TransactionType type, BigDecimal amountChange, BigDecimal balanceAfter,
			Product product, String description) {
		this.card = card;
		this.type = type;
		this.amountChange = amountChange;
		this.balanceAfter = balanceAfter;
		this.product = product;
		this.description = description;
	}

	public Long getId() {
		return id;
	}

	public Card getCard() {
		return card;
	}

	public TransactionType getType() {
		return type;
	}

	public BigDecimal getAmountChange() {
		return amountChange;
	}

	public BigDecimal getBalanceAfter() {
		return balanceAfter;
	}

	public Product getProduct() {
		return product;
	}

	public String getDescription() {
		return description;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}
}
