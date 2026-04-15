package com.bodrumaquapark.entity;

import java.math.BigDecimal;
import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "cards")
public class Card {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, unique = true, length = 128)
	private String uid;

	@Column(nullable = false, precision = 12, scale = 2)
	private BigDecimal balance = BigDecimal.ZERO;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 32)
	private CardStatus status = CardStatus.ACTIVE;

	/**
	 * Turnike: bilet satışı sonrası 1 yapılır; turnike ilk geçişte 0’a düşer. Bakiye mantığından bağımsız tek geçiş hakkı.
	 */
	@Column(name = "entry_gate", nullable = false)
	private int entryGate = 0;

	@Version
	private Long version;

	@Column(nullable = false, updatable = false)
	private Instant createdAt;

	@Column(nullable = false)
	private Instant updatedAt;

	@PrePersist
	void onCreate() {
		Instant now = Instant.now();
		createdAt = now;
		updatedAt = now;
	}

	@PreUpdate
	void onUpdate() {
		updatedAt = Instant.now();
	}

	protected Card() {
	}

	public Card(String uid, BigDecimal balance) {
		this.uid = uid;
		this.balance = balance;
	}

	public Long getId() {
		return id;
	}

	public String getUid() {
		return uid;
	}

	public void setUid(String uid) {
		this.uid = uid;
	}

	public BigDecimal getBalance() {
		return balance;
	}

	public void setBalance(BigDecimal balance) {
		this.balance = balance;
	}

	public CardStatus getStatus() {
		return status;
	}

	public void setStatus(CardStatus status) {
		this.status = status;
	}

	public int getEntryGate() {
		return entryGate;
	}

	public void setEntryGate(int entryGate) {
		this.entryGate = entryGate;
	}

	public Long getVersion() {
		return version;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}
}
