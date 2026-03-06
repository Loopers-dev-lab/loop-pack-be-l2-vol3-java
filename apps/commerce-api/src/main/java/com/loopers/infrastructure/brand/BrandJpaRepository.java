package com.loopers.infrastructure.brand;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;

public interface BrandJpaRepository extends JpaRepository<BrandEntity, Long> {

    Optional<BrandEntity> findByName(String name);

    Optional<BrandEntity> findByIdAndDeletedAtIsNull(Long id);

    Page<BrandEntity> findAllByDeletedAtIsNull(Pageable pageable);

    boolean existsByName(String name);
}
