package com.bodrumaquapark.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.bodrumaquapark.entity.TicketAgeGroup;

public interface TicketAgeGroupRepository extends JpaRepository<TicketAgeGroup, Long> {

	List<TicketAgeGroup> findByActiveTrueOrderBySortOrderAscIdAsc();

	List<TicketAgeGroup> findAllByOrderBySortOrderAscIdAsc();
}
