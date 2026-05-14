package com.bodrumaquapark.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.bodrumaquapark.entity.TurnstileDevice;

public interface TurnstileDeviceRepository extends JpaRepository<TurnstileDevice, Long> {

	Optional<TurnstileDevice> findByDeviceId(String deviceId);

	Optional<TurnstileDevice> findByDeviceIdAndActiveIsTrue(String deviceId);

	List<TurnstileDevice> findAllByOrderByDeviceIdAsc();
}
