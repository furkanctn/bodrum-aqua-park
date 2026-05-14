package com.bodrumaquapark.repository;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.bodrumaquapark.entity.CardPass;
import com.bodrumaquapark.entity.PassType;

public interface CardPassRepository extends JpaRepository<CardPass, Long> {

	Optional<CardPass> findByRfidCard_IdAndValidDateAndPassType(Long rfidCardId, LocalDate validDate, PassType passType);

	List<CardPass> findByRfidCard_IdAndValidDate(Long rfidCardId, LocalDate validDate);

	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query("update CardPass c set c.used = true, c.updatedAt = :ts where c.id = :id and c.used = false")
	int markConsumedIfUnused(@Param("id") Long id, @Param("ts") Instant ts);
}
