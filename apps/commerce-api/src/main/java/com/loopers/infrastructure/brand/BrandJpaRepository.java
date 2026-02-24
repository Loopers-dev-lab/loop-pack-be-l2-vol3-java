package com.loopers.infrastructure.brand;

import com.loopers.domain.brand.Brand;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BrandJpaRepository extends JpaRepository<Brand, Long> {

    // Query

    boolean existsByName(String name);

    boolean existsByNameAndIdNot(String name, Long id);

    @Query(value = "SELECT b FROM Brand b "
                 + "WHERE (:name IS NULL OR b.name LIKE %:name%) "
                 + "AND (:deleted IS NULL OR (:deleted = true AND b.deletedAt IS NOT NULL) OR (:deleted = false AND b.deletedAt IS NULL)) "
                 + "ORDER BY b.createdAt DESC",
           countQuery = "SELECT COUNT(b) FROM Brand b "
                      + "WHERE (:name IS NULL OR b.name LIKE %:name%) "
                      + "AND (:deleted IS NULL OR (:deleted = true AND b.deletedAt IS NOT NULL) OR (:deleted = false AND b.deletedAt IS NULL))")
    Page<Brand> findAll(@Param("name") String name, @Param("deleted") Boolean deleted, Pageable pageable);
}
