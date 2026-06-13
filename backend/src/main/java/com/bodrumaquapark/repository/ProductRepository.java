package com.bodrumaquapark.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.bodrumaquapark.entity.Product;

import jakarta.persistence.LockModeType;

public interface ProductRepository extends JpaRepository<Product, Long> {

	@EntityGraph(attributePaths = "menuPage")
	@Override
	Optional<Product> findById(Long id);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("SELECT p FROM Product p WHERE p.id = :id")
	Optional<Product> findByIdForUpdate(@Param("id") Long id);

	@EntityGraph(attributePaths = "menuPage")
	List<Product> findByActiveTrueAndMenuPage_IdOrderByNameAsc(Long menuPageId);

	@EntityGraph(attributePaths = "menuPage")
	List<Product> findByActiveTrueAndMenuPage_IdInOrderByMenuPage_SortOrderAscNameAsc(Collection<Long> menuPageIds);

	@EntityGraph(attributePaths = "menuPage")
	@Query("SELECT p FROM Product p ORDER BY p.menuPage.sortOrder ASC, p.menuPage.id ASC, p.name ASC")
	List<Product> findAllForAdmin();

	@EntityGraph(attributePaths = "menuPage")
	List<Product> findByMenuPageIsNull();

	boolean existsByMenuPage_IdAndName(Long menuPageId, String name);

	long countByMenuPage_Id(Long menuPageId);

	long countByMenuPage_IdAndActiveTrue(Long menuPageId);

	@Query("SELECT count(p) FROM Product p WHERE p.menuPage.id IN "
			+ "(SELECT mp.id FROM SaleArea sa JOIN sa.menuPages mp WHERE sa.id = :saleAreaId)")
	long countBySaleAreaMenus(@Param("saleAreaId") Long saleAreaId);

	@Query("SELECT count(p) FROM Product p WHERE p.active = true AND p.menuPage.id IN "
			+ "(SELECT mp.id FROM SaleArea sa JOIN sa.menuPages mp WHERE sa.id = :saleAreaId)")
	long countActiveBySaleAreaMenus(@Param("saleAreaId") Long saleAreaId);
}
