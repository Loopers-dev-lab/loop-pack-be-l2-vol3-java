package com.loopers.infrastructure.brand;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;
import java.util.UUID;

public interface BrandJpaRepository extends JpaRepository<BrandEntity, Long> {

    Optional<BrandEntity> findByName(String name);

    Optional<BrandEntity> findByReferenceId(UUID referenceId);

    Optional<BrandEntity> findByReferenceIdAndDeletedAtIsNull(UUID referenceId);

    Page<BrandEntity> findAllByDeletedAtIsNull(Pageable pageable);

    boolean existsByName(String name);

    boolean existsByReferenceIdAndDeletedAtIsNull(UUID referenceId);
}
