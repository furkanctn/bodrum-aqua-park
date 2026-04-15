package com.bodrumaquapark.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.bodrumaquapark.entity.StaffUser;

public interface StaffUserRepository extends JpaRepository<StaffUser, Long> {

	@EntityGraph(attributePaths = "saleAreas")
	@Query("select u from StaffUser u where u.userId = :uid")
	Optional<StaffUser> loadWithSaleAreasByUserId(@Param("uid") String userId);

	@Query("select case when count(u) > 0 then true else false end from StaffUser u where u.userId = :uid")
	boolean existsStaffUserByUserId(@Param("uid") String userId);

	@EntityGraph(attributePaths = "saleAreas")
	@Query("select u from StaffUser u order by u.userId asc")
	List<StaffUser> loadAllWithSaleAreasOrderByUserId();

	@EntityGraph(attributePaths = "saleAreas")
	@Query("select u from StaffUser u where u.id = :id")
	Optional<StaffUser> loadWithSaleAreasById(@Param("id") Long id);
}
