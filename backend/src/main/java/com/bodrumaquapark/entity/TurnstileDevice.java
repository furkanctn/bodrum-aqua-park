package com.bodrumaquapark.entity;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(
		name = "turnstile_devices",
		uniqueConstraints = @UniqueConstraint(name = "uk_turnstile_devices_device_id", columnNames = "device_id"),
		indexes = {
				@Index(name = "idx_turnstile_devices_last_seen", columnList = "last_seen_at")
		})
public class TurnstileDevice {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	/** Turnike / Raspberry tarafında kullanılan harici kimlik (örn. TURN-PI-1). */
	@Column(name = "device_id", nullable = false, unique = true, length = 64)
	private String deviceId;

	@Column(name = "device_token_hash", nullable = false, length = 120)
	private String deviceTokenHash;

	@Column(name = "label", length = 128)
	private String label;

	@Column(name = "active", nullable = false)
	private boolean active = true;

	/** Son başarılı X-DEVICE-TOKEN doğrulaması (kart sonucundan bağımsız). */
	@Column(name = "last_seen_at")
	private Instant lastSeenAt;

	/** Son onaylı geçiş (allowed=true). */
	@Column(name = "last_successful_access_at")
	private Instant lastSuccessfulAccessAt;

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

	protected TurnstileDevice() {
	}

	public TurnstileDevice(String deviceId, String deviceTokenHash, String label, boolean active) {
		this.deviceId = deviceId;
		this.deviceTokenHash = deviceTokenHash;
		this.label = label;
		this.active = active;
	}

	public Long getId() {
		return id;
	}

	public String getDeviceId() {
		return deviceId;
	}

	public void setDeviceId(String deviceId) {
		this.deviceId = deviceId;
	}

	public String getDeviceTokenHash() {
		return deviceTokenHash;
	}

	public void setDeviceTokenHash(String deviceTokenHash) {
		this.deviceTokenHash = deviceTokenHash;
	}

	public String getLabel() {
		return label;
	}

	public void setLabel(String label) {
		this.label = label;
	}

	public boolean isActive() {
		return active;
	}

	public void setActive(boolean active) {
		this.active = active;
	}

	public Instant getLastSeenAt() {
		return lastSeenAt;
	}

	public void setLastSeenAt(Instant lastSeenAt) {
		this.lastSeenAt = lastSeenAt;
	}

	public Instant getLastSuccessfulAccessAt() {
		return lastSuccessfulAccessAt;
	}

	public void setLastSuccessfulAccessAt(Instant lastSuccessfulAccessAt) {
		this.lastSuccessfulAccessAt = lastSuccessfulAccessAt;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}
}
