package com.bodrumaquapark.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.bodrumaquapark.entity.MenuPage;

public interface MenuPageRepository extends JpaRepository<MenuPage, Long> {

	Optional<MenuPage> findByCode(String code);

	boolean existsByCode(String code);

	List<MenuPage> findAllByOrderBySortOrderAscIdAsc();
}
