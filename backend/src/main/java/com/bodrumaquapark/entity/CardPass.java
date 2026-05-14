package com.bodrumaquapark.entity;

import java.time.Instant;
import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(
		name = "rfid_card_passes",
		uniqueConstraints = @UniqueConstraint(
				name = "uk_rfid_pass_card_date_type",
				columnNames = { "rfid_card_id", "valid_date", "pass_type" }),
		indexes = {
				@Index(name = "idx_rfid_pass_valid_date", columnList = "valid_date"),
				@Index(name = "idx_rfid_pass_card_valid", columnList = "rfid_card_id, valid_date"),
				@Index(name = "idx_rfid_pass_card_valid_active", columnList = "rfid_card_id, valid_date, active"),
				@Index(name = "idx_rfid_pass_valid_active_used", columnList = "valid_date, active, used")
		})
public class CardPass {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "rfid_card_id", nullable = false)
	private RfidCard rfidCard;

	@Column(name = "valid_date", nullable = false)
	private LocalDate validDate;

	@Enumerated(EnumType.STRING)
	@Column(name = "pass_type", nullable = false, length = 32)
	private PassType passType;

	@Column(name = "active", nullable = false)
	private boolean active = true;

	@Column(name = "used", nullable = false)
	private boolean used = false;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
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

	protected CardPass() {
	}

	public CardPass(RfidCard rfidCard, LocalDate validDate, PassType passType, boolean active, boolean used) {
		this.rfidCard = rfidCard;
		this.validDate = validDate;
		this.passType = passType;
		this.active = active;
		this.used = used;
	}

	public Long getId() {
		return id;
	}

	public RfidCard getRfidCard() {
		return rfidCard;
	}

	public void setRfidCard(RfidCard rfidCard) {
		this.rfidCard = rfidCard;
	}

	public LocalDate getValidDate() {
		return validDate;
	}

	public void setValidDate(LocalDate validDate) {
		this.validDate = validDate;
	}

	public PassType getPassType() {
		return passType;
	}

	public void setPassType(PassType passType) {
		this.passType = passType;
	}

	public boolean isActive() {
		return active;
	}

	public void setActive(boolean active) {
		this.active = active;
	}

	public boolean isUsed() {
		return used;
	}

	public void setUsed(boolean used) {
		this.used = used;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}
}
