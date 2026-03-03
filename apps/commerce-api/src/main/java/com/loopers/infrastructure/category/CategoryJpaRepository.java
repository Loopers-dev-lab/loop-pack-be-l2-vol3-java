package com.loopers.infrastructure.category;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface CategoryJpaRepository extends JpaRepository<CategoryEntity, UUID> {
    Optional<CategoryEntity> findByIdAndDeletedAtIsNull(UUID id);

    Page<CategoryEntity> findAllByDeletedAtIsNull(Pageable pageable);

    boolean existsByIdAndDeletedAtIsNull(UUID id);
}
