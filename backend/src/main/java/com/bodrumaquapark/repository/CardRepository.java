package com.bodrumaquapark.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.bodrumaquapark.entity.Card;

import jakarta.persistence.LockModeType;

public interface CardRepository extends JpaRepository<Card, Long> {

	Optional<Card> findByUid(String uid);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("SELECT c FROM Card c WHERE c.uid = :uid")
	Optional<Card> findByUidForUpdate(@Param("uid") String uid);

	boolean existsByUid(String uid);
}
