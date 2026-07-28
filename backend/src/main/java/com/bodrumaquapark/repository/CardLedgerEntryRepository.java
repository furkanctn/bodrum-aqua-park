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

	@EntityGraph(attributePaths = { "product", "product.menuPage", "saleArea" })
	@Query("select e from CardLedgerEntry e where e.card.uid = :uid "
			+ "and e.createdAt >= :from and e.createdAt < :to order by e.createdAt desc")
	List<CardLedgerEntry> findByCardUidAndCreatedAtRange(@Param("uid") String uid, @Param("from") Instant from,
			@Param("to") Instant to);

	Optional<CardLedgerEntry> findFirstByCard_IdAndTypeInOrderByCreatedAtAscIdAsc(
			Long cardId,
			Collection<TransactionType> types);

	@Query("select e from CardLedgerEntry e where e.card.id = :cardId and e.type in :types "
			+ "and e.createdAt >= :from and e.createdAt < :to order by e.createdAt asc, e.id asc")
	List<CardLedgerEntry> findBalanceLoadsInRange(@Param("cardId") Long cardId,
			@Param("types") Collection<TransactionType> types, @Param("from") Instant from, @Param("to") Instant to);

	default Optional<CardLedgerEntry> findFirstByCard_IdAndTypeInAndCreatedAtRange(
			Long cardId, Collection<TransactionType> types, Instant from, Instant to) {
		List<CardLedgerEntry> list = findBalanceLoadsInRange(cardId, types, from, to);
		return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
	}

	boolean existsByCard_IdAndTypeIn(Long cardId, Collection<TransactionType> types);

	@Query("select count(e) from CardLedgerEntry e "
			+ "where e.card.id = :cardId and e.type in :types "
			+ "and e.createdAt >= :from and e.createdAt < :to")
	long countByCard_IdAndTypeInAndCreatedAtRange(@Param("cardId") Long cardId,
			@Param("types") Collection<TransactionType> types, @Param("from") Instant from, @Param("to") Instant to);

	default boolean existsByCard_IdAndTypeInAndCreatedAtRange(
			Long cardId, Collection<TransactionType> types, Instant from, Instant to) {
		return countByCard_IdAndTypeInAndCreatedAtRange(cardId, types, from, to) > 0;
	}

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

	@Query("select sa.code, sa.name, p.id, p.name, count(e), coalesce(sum(-e.amountChange), 0) "
			+ "from CardLedgerEntry e join e.saleArea sa join e.product p "
			+ "where e.type = :saleType and e.createdAt >= :from and e.createdAt < :to "
			+ "group by sa.code, sa.name, p.id, p.name "
			+ "order by sa.name asc, coalesce(sum(-e.amountChange), 0) desc")
	List<Object[]> aggregateProductSalesBySaleAreaAndProduct(@Param("saleType") TransactionType saleType,
			@Param("from") Instant from, @Param("to") Instant to);

	@Query("select e.type, count(e), coalesce(sum(e.amountChange), 0) from CardLedgerEntry e "
			+ "where e.createdAt >= :from and e.createdAt < :to group by e.type")
	List<Object[]> aggregateByTransactionType(@Param("from") Instant from, @Param("to") Instant to);

	@Query("select tag.name, coalesce(sum(e.lineQuantity), 0) from CardLedgerEntry e join e.ticketAgeGroup tag "
			+ "where tag.agencyComplimentary = true and e.ticketAgeGroup is not null "
			+ "and e.createdAt >= :from and e.createdAt < :to "
			+ "group by tag.id, tag.name, tag.sortOrder order by tag.sortOrder asc, tag.id asc")
	List<Object[]> aggregateAgencyTicketCounts(@Param("from") Instant from, @Param("to") Instant to);

	@Query("select coalesce(sum(e.lineQuantity), 0) from CardLedgerEntry e join e.ticketAgeGroup tag "
			+ "where e.ticketAgeGroup is not null and tag.agencyComplimentary = false and e.type in :types "
			+ "and e.createdAt >= :from and e.createdAt < :to")
	long aggregateTicketLineQuantity(@Param("types") Collection<TransactionType> types, @Param("from") Instant from,
			@Param("to") Instant to);

	@Query("select e from CardLedgerEntry e join fetch e.card where e.type in :types and e.ticketAgeGroup is null "
			+ "and e.amountChange > 0 and e.createdAt >= :from and e.createdAt < :to")
	List<CardLedgerEntry> findLegacyPaidTicketPaymentEntries(@Param("types") Collection<TransactionType> types,
			@Param("from") Instant from, @Param("to") Instant to);

	@Query("select count(e) from CardLedgerEntry e join e.ticketAgeGroup tag "
			+ "where e.card.id = :cardId and e.ticketAgeGroup is not null and tag.agencyComplimentary = false "
			+ "and e.type in :types and e.createdAt >= :from and e.createdAt < :to")
	long countPaidNonAgencyTicketLinesOnCard(@Param("cardId") Long cardId, @Param("types") Collection<TransactionType> types,
			@Param("from") Instant from, @Param("to") Instant to);

	@Query("select tag.name, coalesce(sum(e.lineQuantity), 0) from CardLedgerEntry e join e.ticketAgeGroup tag "
			+ "where e.type in :types and tag.agencyComplimentary = false and e.createdAt >= :from and e.createdAt < :to "
			+ "group by tag.id, tag.name, tag.sortOrder order by tag.sortOrder asc, tag.id asc")
	List<Object[]> aggregateTicketQuantitiesByAgeGroupName(@Param("types") Collection<TransactionType> types,
			@Param("from") Instant from, @Param("to") Instant to);

	@Query("select coalesce(sum(e.amountChange), 0) from CardLedgerEntry e "
			+ "where e.type = :type and e.createdAt >= :from and e.createdAt < :to")
	java.math.BigDecimal sumAmountByType(@Param("type") TransactionType type, @Param("from") Instant from,
			@Param("to") Instant to);

	@Query("select coalesce(sum(-e.amountChange), 0) from CardLedgerEntry e "
			+ "where e.type = :refundType and e.amountChange < 0 and e.createdAt >= :from and e.createdAt < :to")
	java.math.BigDecimal sumNegativeAmountByType(@Param("refundType") TransactionType refundType,
			@Param("from") Instant from, @Param("to") Instant to);

	@Query("select count(e) from CardLedgerEntry e where e.type = :refundType and e.amountChange < 0 "
			+ "and e.createdAt >= :from and e.createdAt < :to")
	long countNegativeAmountByType(@Param("refundType") TransactionType refundType, @Param("from") Instant from,
			@Param("to") Instant to);

	@Query("select count(e) from CardLedgerEntry e where e.type = :resetType "
			+ "and e.description like :descriptionPrefix and e.createdAt >= :from and e.createdAt < :to")
	long countByTypeAndDescriptionPrefix(@Param("resetType") TransactionType resetType,
			@Param("descriptionPrefix") String descriptionPrefix, @Param("from") Instant from, @Param("to") Instant to);

	@EntityGraph(attributePaths = { "product", "product.menuPage", "saleArea", "card" })
	@Query("select e from CardLedgerEntry e where e.createdAt >= :from and e.createdAt < :to")
	Page<CardLedgerEntry> findLedgerPageForRange(@Param("from") Instant from, @Param("to") Instant to, Pageable pageable);
}
