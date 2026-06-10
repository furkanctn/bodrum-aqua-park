package com.bodrumaquapark.repository;

import java.util.Collection;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.bodrumaquapark.entity.RfidCard;

public interface RfidCardRepository extends JpaRepository<RfidCard, Long> {

	Optional<RfidCard> findByCardId(String cardId);

	Optional<RfidCard> findFirstByCardIdIn(Collection<String> cardIds);
}
