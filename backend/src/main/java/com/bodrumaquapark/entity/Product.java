package com.bodrumaquapark.entity;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "products")
public class Product {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "sale_area_id", nullable = false)
	private SaleArea saleArea;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "menu_page_id")
	private MenuPage menuPage;

	@Column(nullable = false, length = 255)
	private String name;

	@Column(nullable = false, precision = 12, scale = 2)
	private BigDecimal price;

	@Column(nullable = false)
	private boolean active = true;

	/** null = stok takibi yok (sınırsız) */
	@Column(name = "stock_quantity")
	private Integer stockQuantity;

	protected Product() {
	}

	public Product(SaleArea saleArea, MenuPage menuPage, String name, BigDecimal price, Integer stockQuantity) {
		this.saleArea = saleArea;
		this.menuPage = menuPage;
		this.name = name;
		this.price = price;
		this.stockQuantity = stockQuantity;
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

	public MenuPage getMenuPage() {
		return menuPage;
	}

	public void setMenuPage(MenuPage menuPage) {
		this.menuPage = menuPage;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public BigDecimal getPrice() {
		return price;
	}

	public void setPrice(BigDecimal price) {
		this.price = price;
	}

	public boolean isActive() {
		return active;
	}

	public void setActive(boolean active) {
		this.active = active;
	}

	public Integer getStockQuantity() {
		return stockQuantity;
	}

	public void setStockQuantity(Integer stockQuantity) {
		this.stockQuantity = stockQuantity;
	}
}
