package com.bodrumaquapark.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.bodrumaquapark.entity.AppSetting;

public interface AppSettingRepository extends JpaRepository<AppSetting, Long> {

	Optional<AppSetting> findBySettingKey(String settingKey);
}
