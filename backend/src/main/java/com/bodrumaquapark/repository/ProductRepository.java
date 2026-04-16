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

	@EntityGraph(attributePaths = { "saleArea", "menuPage" })
	@Override
	Optional<Product> findById(Long id);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("SELECT p FROM Product p WHERE p.id = :id")
	Optional<Product> findByIdForUpdate(@Param("id") Long id);

	@EntityGraph(attributePaths = { "saleArea", "menuPage" })
	List<Product> findByActiveTrueAndSaleArea_CodeOrderByNameAsc(String saleAreaCode);

	@EntityGraph(attributePaths = { "saleArea", "menuPage" })
	List<Product> findByActiveTrueAndMenuPage_IdOrderByNameAsc(Long menuPageId);

	@EntityGraph(attributePaths = { "saleArea", "menuPage" })
	List<Product> findByActiveTrueOrderBySaleArea_CodeAscNameAsc();

	@EntityGraph(attributePaths = { "saleArea", "menuPage" })
	List<Product> findByActiveTrueAndSaleArea_CodeInOrderBySaleArea_CodeAscNameAsc(Collection<String> saleAreaCodes);

	@EntityGraph(attributePaths = { "saleArea", "menuPage" })
	@Query("SELECT p FROM Product p ORDER BY p.saleArea.code ASC, p.name ASC")
	List<Product> findAllWithSaleAreaForAdmin();

	@EntityGraph(attributePaths = "saleArea")
	List<Product> findByMenuPageIsNull();

	boolean existsBySaleArea_CodeAndName(String saleAreaCode, String name);

	long countBySaleArea_Id(Long saleAreaId);

	long countBySaleArea_IdAndActiveTrue(Long saleAreaId);

	long countByMenuPage_Id(Long menuPageId);
}
