package com.bodrumaquapark.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.bodrumaquapark.entity.SaleArea;

public interface SaleAreaRepository extends JpaRepository<SaleArea, Long> {

	Optional<SaleArea> findByCode(String code);

	@EntityGraph(attributePaths = "menuPages")
	Optional<SaleArea> findWithMenuPagesByCode(String code);

	@EntityGraph(attributePaths = "menuPages")
	Optional<SaleArea> findWithMenuPagesById(Long id);

	@EntityGraph(attributePaths = "menuPages")
	List<SaleArea> findAllByCodeIn(Collection<String> codes);

	@EntityGraph(attributePaths = "menuPages")
	@Query("SELECT sa FROM SaleArea sa")
	List<SaleArea> findAllWithMenuPages();

	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query(value = "DELETE FROM staff_user_sale_areas WHERE sale_area_id = :saleAreaId", nativeQuery = true)
	void deleteStaffUserAssignmentsBySaleAreaId(@Param("saleAreaId") Long saleAreaId);
}
