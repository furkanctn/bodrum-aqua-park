package com.bodrumaquapark.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "app_settings", uniqueConstraints = @UniqueConstraint(name = "uk_app_settings_key", columnNames = "setting_key"))
public class AppSetting {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "setting_key", nullable = false, length = 120)
	private String settingKey;

	@Column(name = "setting_value", length = 512)
	private String settingValue;

	public AppSetting() {
	}

	public AppSetting(String settingKey, String settingValue) {
		this.settingKey = settingKey;
		this.settingValue = settingValue;
	}

	public Long getId() {
		return id;
	}

	public String getSettingKey() {
		return settingKey;
	}

	public void setSettingKey(String settingKey) {
		this.settingKey = settingKey;
	}

	public String getSettingValue() {
		return settingValue;
	}

	public void setSettingValue(String settingValue) {
		this.settingValue = settingValue;
	}
}
