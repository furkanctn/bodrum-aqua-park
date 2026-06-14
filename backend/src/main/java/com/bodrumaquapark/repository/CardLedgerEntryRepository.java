package com.bodrumaquapark.repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.bodrumaquapark.entity.CardLedgerEntry;
import com.bodrumaquapark.entity.TransactionType;

public interface CardLedgerEntryRepository extends JpaRepository<CardLedgerEntry, Long> {

	@EntityGraph(attributePaths = { "product", "product.menuPage", "saleArea" })
	List<CardLedgerEntry> findByCard_UidOrderByCreatedAtDesc(String uid);

	Optional<CardLedgerEntry> findFirstByCard_IdAndTypeInOrderByCreatedAtAscIdAsc(
			Long cardId,
			Collection<TransactionType> types);

	@Query("select sa.code, sa.name, count(e), coalesce(sum(-e.amountChange), 0) from CardLedgerEntry e join e.saleArea sa "
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

	@Query("select tag.name, coalesce(sum(e.lineQuantity), 0) from CardLedgerEntry e join e.ticketAgeGroup tag "
			+ "where tag.agencyComplimentary = true and e.ticketAgeGroup is not null "
			+ "and e.createdAt >= :from and e.createdAt < :to "
			+ "group by tag.id, tag.name, tag.sortOrder order by tag.sortOrder asc, tag.id asc")
	List<Object[]> aggregateAgencyTicketCounts(@Param("from") Instant from, @Param("to") Instant to);

	@Query("select coalesce(sum(case when e.lineQuantity is not null then e.lineQuantity else 1 end), 0) "
			+ "from CardLedgerEntry e where e.type in :types and e.createdAt >= :from and e.createdAt < :to")
	long aggregateTicketEntryCount(@Param("types") Collection<TransactionType> types, @Param("from") Instant from,
			@Param("to") Instant to);

	@EntityGraph(attributePaths = { "product", "product.menuPage", "saleArea", "card" })
	@Query("select e from CardLedgerEntry e where e.createdAt >= :from and e.createdAt < :to")
	Page<CardLedgerEntry> findLedgerPageForRange(@Param("from") Instant from, @Param("to") Instant to, Pageable pageable);
}
