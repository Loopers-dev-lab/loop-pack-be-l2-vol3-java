package com.loopers.infrastructure.product;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ProductJpaRepository extends JpaRepository<ProductJpaEntity, Long> {

    Page<ProductJpaEntity> findAllByBrandIdAndDeletedAtIsNull(Long brandId, Pageable pageable);

    Page<ProductJpaEntity> findAllByDeletedAtIsNull(Pageable pageable);

    List<ProductJpaEntity> findAllByBrandIdAndDeletedAtIsNull(Long brandId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM ProductJpaEntity p WHERE p.id = :id")
    Optional<ProductJpaEntity> findByIdForUpdate(@Param("id") Long id);
}
