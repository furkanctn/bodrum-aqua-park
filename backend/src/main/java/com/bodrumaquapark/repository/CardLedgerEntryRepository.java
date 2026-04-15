package com.bodrumaquapark.repository;

import java.util.List;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import com.bodrumaquapark.entity.CardLedgerEntry;

public interface CardLedgerEntryRepository extends JpaRepository<CardLedgerEntry, Long> {

	@EntityGraph(attributePaths = { "product", "product.saleArea" })
	List<CardLedgerEntry> findByCard_UidOrderByCreatedAtDesc(String uid);
}
