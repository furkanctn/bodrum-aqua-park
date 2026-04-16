package com.bodrumaquapark.repository;

import java.time.Instant;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.bodrumaquapark.entity.CardLedgerEntry;
import com.bodrumaquapark.entity.TransactionType;

public interface CardLedgerEntryRepository extends JpaRepository<CardLedgerEntry, Long> {

	@EntityGraph(attributePaths = { "product", "product.saleArea" })
	List<CardLedgerEntry> findByCard_UidOrderByCreatedAtDesc(String uid);

	@Query("select sa.code, sa.name, count(e), coalesce(sum(-e.amountChange), 0) from CardLedgerEntry e join e.product p join p.saleArea sa "
			+ "where e.type = :saleType and e.createdAt >= :from and e.createdAt < :to "
			+ "group by sa.code, sa.name order by coalesce(sum(-e.amountChange), 0) desc")
	List<Object[]> aggregateProductSalesBySaleArea(@Param("saleType") TransactionType saleType,
			@Param("from") Instant from, @Param("to") Instant to);

	@Query("select p.id, p.name, count(e), coalesce(sum(-e.amountChange), 0) from CardLedgerEntry e join e.product p "
			+ "where e.type = :saleType and e.createdAt >= :from and e.createdAt < :to "
			+ "group by p.id, p.name order by coalesce(sum(-e.amountChange), 0) desc")
	List<Object[]> aggregateProductSalesByProduct(@Param("saleType") TransactionType saleType,
			@Param("from") Instant from, @Param("to") Instant to);

	@Query("select e.type, count(e), coalesce(sum(e.amountChange), 0) from CardLedgerEntry e "
			+ "where e.createdAt >= :from and e.createdAt < :to group by e.type")
	List<Object[]> aggregateByTransactionType(@Param("from") Instant from, @Param("to") Instant to);

	@EntityGraph(attributePaths = { "product", "product.saleArea", "card" })
	@Query("select e from CardLedgerEntry e where e.createdAt >= :from and e.createdAt < :to")
	Page<CardLedgerEntry> findLedgerPageForRange(@Param("from") Instant from, @Param("to") Instant to, Pageable pageable);
}
