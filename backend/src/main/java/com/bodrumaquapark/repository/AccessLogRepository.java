package com.bodrumaquapark.repository;

import java.time.Instant;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.bodrumaquapark.entity.AccessLog;

public interface AccessLogRepository extends JpaRepository<AccessLog, Long> {

	Page<AccessLog> findByCreatedAtBetweenOrderByCreatedAtDesc(Instant fromInclusive, Instant toExclusive, Pageable pageable);

	Page<AccessLog> findByDeviceIdAndCreatedAtBetweenOrderByCreatedAtDesc(
			String deviceId,
			Instant fromInclusive,
			Instant toExclusive,
			Pageable pageable);

	Page<AccessLog> findByCardIdAndCreatedAtBetweenOrderByCreatedAtDesc(
			String cardId,
			Instant fromInclusive,
			Instant toExclusive,
			Pageable pageable);

	Page<AccessLog> findByDeviceIdAndCardIdAndCreatedAtBetweenOrderByCreatedAtDesc(
			String deviceId,
			String cardId,
			Instant fromInclusive,
			Instant toExclusive,
			Pageable pageable);

	long countByDeviceIdAndAllowedFalseAndCreatedAtBetween(String deviceId, Instant fromInclusive, Instant toExclusive);
}
