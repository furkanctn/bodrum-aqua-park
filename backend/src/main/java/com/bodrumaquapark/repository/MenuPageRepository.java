package com.bodrumaquapark.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.bodrumaquapark.entity.MenuPage;

public interface MenuPageRepository extends JpaRepository<MenuPage, Long> {

	@EntityGraph(attributePaths = "saleArea")
	Optional<MenuPage> findById(Long id);

	@EntityGraph(attributePaths = "saleArea")
	Optional<MenuPage> findBySaleArea_IdAndCode(Long saleAreaId, String code);

	List<MenuPage> findBySaleArea_IdOrderBySortOrderAscIdAsc(Long saleAreaId);

	@EntityGraph(attributePaths = "saleArea")
	List<MenuPage> findBySaleArea_CodeInOrderBySaleArea_CodeAscSortOrderAscIdAsc(Collection<String> saleAreaCodes);

	long countBySaleArea_Id(Long saleAreaId);

	boolean existsBySaleArea_IdAndCode(Long saleAreaId, String code);

	void deleteBySaleArea_Id(Long saleAreaId);

	@EntityGraph(attributePaths = "saleArea")
	@Query("SELECT m FROM MenuPage m")
	List<MenuPage> findAllWithSaleArea();
}
