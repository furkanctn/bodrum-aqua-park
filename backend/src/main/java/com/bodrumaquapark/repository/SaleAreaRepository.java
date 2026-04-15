package com.bodrumaquapark.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.bodrumaquapark.entity.SaleArea;

public interface SaleAreaRepository extends JpaRepository<SaleArea, Long> {

	Optional<SaleArea> findByCode(String code);

	List<SaleArea> findAllByCodeIn(Collection<String> codes);
}
