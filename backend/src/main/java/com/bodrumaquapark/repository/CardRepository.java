package com.bodrumaquapark.repository;

import java.util.Collection;
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

	Optional<Card> findFirstByUidIn(Collection<String> uids);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("SELECT c FROM Card c WHERE c.uid IN :uids ORDER BY c.id")
	Optional<Card> findFirstByUidInForUpdate(@Param("uids") Collection<String> uids);
}
