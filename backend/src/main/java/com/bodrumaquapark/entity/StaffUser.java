package com.bodrumaquapark.entity;

import java.util.HashSet;
import java.util.Set;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;

@Entity
@Table(name = "staff_users")
public class StaffUser {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	/** Sicil / kullanıcı kodu */
	@Column(nullable = false, unique = true, length = 64)
	private String userId;

	@Column(nullable = false, length = 120)
	private String passwordHash;

	@Column(length = 120)
	private String displayName;

	@Column(nullable = false)
	private boolean active = true;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 32)
	private RoleCode role = RoleCode.CASHIER;

	/** Boş: yönetici için tüm alanlar anlamına gelir (JWT üretiminde genişletilir) */
	@ManyToMany(fetch = FetchType.LAZY)
	@JoinTable(name = "staff_user_sale_areas", joinColumns = @JoinColumn(name = "staff_user_id"),
			inverseJoinColumns = @JoinColumn(name = "sale_area_id"))
	private Set<SaleArea> saleAreas = new HashSet<>();

	/** POS: bilet / kart satış ekranı (Kart satış) */
	@Column(nullable = false)
	private boolean ticketSalesAllowed = true;

	/** POS: bakiye yükleme ekranı */
	@Column(nullable = false)
	private boolean balanceLoadAllowed = true;

	/** Yönetim paneli (/admin.html) ve /api/admin/* — ADMIN rolünde her zaman anlamlıdır */
	@Column(name = "admin_panel_access", nullable = false)
	private boolean adminPanelAccess = false;

	@Version
	private Long version;

	@Column(nullable = false, updatable = false)
	private Instant createdAt;

	@Column(nullable = false)
	private Instant updatedAt;

	public StaffUser() {
	}

	public StaffUser(String userId, String passwordHash, String displayName, RoleCode role) {
		this.userId = userId;
		this.passwordHash = passwordHash;
		this.displayName = displayName;
		this.role = role;
	}

	@PrePersist
	void prePersist() {
		Instant now = Instant.now();
		createdAt = now;
		updatedAt = now;
	}

	@PreUpdate
	void preUpdate() {
		updatedAt = Instant.now();
	}

	public Long getId() {
		return id;
	}

	public String getUserId() {
		return userId;
	}

	public void setUserId(String userId) {
		this.userId = userId;
	}

	public String getPasswordHash() {
		return passwordHash;
	}

	public void setPasswordHash(String passwordHash) {
		this.passwordHash = passwordHash;
	}

	public String getDisplayName() {
		return displayName;
	}

	public void setDisplayName(String displayName) {
		this.displayName = displayName;
	}

	public boolean isActive() {
		return active;
	}

	public void setActive(boolean active) {
		this.active = active;
	}

	public RoleCode getRole() {
		return role;
	}

	public void setRole(RoleCode role) {
		this.role = role;
	}

	public Set<SaleArea> getSaleAreas() {
		return saleAreas;
	}

	public void setSaleAreas(Set<SaleArea> saleAreas) {
		this.saleAreas = saleAreas;
	}

	public boolean isTicketSalesAllowed() {
		return ticketSalesAllowed;
	}

	public void setTicketSalesAllowed(boolean ticketSalesAllowed) {
		this.ticketSalesAllowed = ticketSalesAllowed;
	}

	public boolean isBalanceLoadAllowed() {
		return balanceLoadAllowed;
	}

	public void setBalanceLoadAllowed(boolean balanceLoadAllowed) {
		this.balanceLoadAllowed = balanceLoadAllowed;
	}

	public boolean isAdminPanelAccess() {
		return adminPanelAccess;
	}

	public void setAdminPanelAccess(boolean adminPanelAccess) {
		this.adminPanelAccess = adminPanelAccess;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}
}
