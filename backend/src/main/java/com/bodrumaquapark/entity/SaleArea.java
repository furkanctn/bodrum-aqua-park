package com.bodrumaquapark.entity;

import java.util.HashSet;
import java.util.Set;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;

@Entity
@Table(name = "sale_areas")
public class SaleArea {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, unique = true, length = 64)
	private String code;

	@Column(nullable = false, length = 255)
	private String name;

	@ManyToMany(fetch = FetchType.LAZY)
	@JoinTable(name = "sale_area_menu_pages", joinColumns = @JoinColumn(name = "sale_area_id"),
			inverseJoinColumns = @JoinColumn(name = "menu_page_id"))
	private Set<MenuPage> menuPages = new HashSet<>();

	protected SaleArea() {
	}

	public SaleArea(String code, String name) {
		this.code = code;
		this.name = name;
	}

	public Long getId() {
		return id;
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

	public Set<MenuPage> getMenuPages() {
		return menuPages;
	}

	public void setMenuPages(Set<MenuPage> menuPages) {
		this.menuPages = menuPages;
	}
}
