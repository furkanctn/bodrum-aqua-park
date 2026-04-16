package com.bodrumaquapark.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "menu_pages", uniqueConstraints = @UniqueConstraint(columnNames = { "sale_area_id", "code" }))
public class MenuPage {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "sale_area_id", nullable = false)
	private SaleArea saleArea;

	@Column(nullable = false, length = 64)
	private String code;

	@Column(nullable = false, length = 255)
	private String name;

	@Column(nullable = false)
	private int sortOrder = 0;

	protected MenuPage() {
	}

	public MenuPage(SaleArea saleArea, String code, String name, int sortOrder) {
		this.saleArea = saleArea;
		this.code = code;
		this.name = name;
		this.sortOrder = sortOrder;
	}

	public Long getId() {
		return id;
	}

	public SaleArea getSaleArea() {
		return saleArea;
	}

	public void setSaleArea(SaleArea saleArea) {
		this.saleArea = saleArea;
	}

	public String getCode() {
		return code;
	}

	public void setCode(String code) {
		this.code = code;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public int getSortOrder() {
		return sortOrder;
	}

	public void setSortOrder(int sortOrder) {
		this.sortOrder = sortOrder;
	}
}
