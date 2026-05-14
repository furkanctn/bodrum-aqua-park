package com.bodrumaquapark.entity;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

@Entity
@Table(
		name = "access_logs",
		indexes = {
				@Index(name = "idx_access_logs_card_id", columnList = "card_id"),
				@Index(name = "idx_access_logs_device_id", columnList = "device_id"),
				@Index(name = "idx_access_logs_created_at", columnList = "created_at"),
				@Index(name = "idx_access_logs_device_created", columnList = "device_id, created_at"),
				@Index(name = "idx_access_logs_allowed_created", columnList = "allowed, created_at"),
				@Index(name = "idx_access_logs_device_allowed_created", columnList = "device_id, allowed, created_at")
		})
public class AccessLog {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "card_id", nullable = false, length = 128)
	private String cardId;

	@Column(name = "device_id", nullable = false, length = 64)
	private String deviceId;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "turnstile_device_id")
	private TurnstileDevice turnstileDevice;

	@Column(name = "allowed", nullable = false)
	private boolean allowed;

	@Column(name = "reason", nullable = false, length = 512)
	private String reason;

	@Column(name = "ip_address", length = 64)
	private String ipAddress;

	@Column(name = "user_agent", length = 512)
	private String userAgent;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@PrePersist
	void onCreate() {
		if (createdAt == null) {
			createdAt = Instant.now();
		}
	}

	protected AccessLog() {
	}

	public AccessLog(
			String cardId,
			String deviceId,
			TurnstileDevice turnstileDevice,
			boolean allowed,
			String reason,
			String ipAddress,
			String userAgent) {
		this.cardId = cardId;
		this.deviceId = deviceId;
		this.turnstileDevice = turnstileDevice;
		this.allowed = allowed;
		this.reason = reason;
		this.ipAddress = ipAddress;
		this.userAgent = userAgent;
	}

	public Long getId() {
		return id;
	}

	public String getCardId() {
		return cardId;
	}

	public String getDeviceId() {
		return deviceId;
	}

	public TurnstileDevice getTurnstileDevice() {
		return turnstileDevice;
	}

	public boolean isAllowed() {
		return allowed;
	}

	public String getReason() {
		return reason;
	}

	public String getIpAddress() {
		return ipAddress;
	}

	public String getUserAgent() {
		return userAgent;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}
}
