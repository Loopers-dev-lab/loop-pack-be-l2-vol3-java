package com.loopers.infrastructure.category;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CategoryJpaRepository extends JpaRepository<CategoryEntity, Long> {
    Optional<CategoryEntity> findByReferenceIdAndDeletedAtIsNull(UUID referenceId);

    Page<CategoryEntity> findAllByDeletedAtIsNull(Pageable pageable);

    List<CategoryEntity> findAllByDeletedAtIsNullOrderByIdAsc();

    boolean existsByReferenceIdAndDeletedAtIsNull(UUID referenceId);
}
