package com.loopers.infrastructure.product;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ProductJpaRepository extends JpaRepository<ProductEntity, Long> {

    Optional<ProductEntity> findByIdAndDeletedAtIsNull(Long id);

    Page<ProductEntity> findAllByDeletedAtIsNull(Pageable pageable);

    Page<ProductEntity> findAllByBrandIdAndDeletedAtIsNull(Long brandId, Pageable pageable);

    Page<ProductEntity> findAllByBrandId(Long brandId, Pageable pageable);

    @Query("SELECT p.id FROM ProductEntity p WHERE p.brandId = :brandId AND p.deletedAt IS NULL")
    List<Long> findIdsByBrandIdAndDeletedAtIsNull(@Param("brandId") Long brandId);

    @Modifying
    @Query(value = "UPDATE products p SET p.deleted_at = CURRENT_TIMESTAMP WHERE p.brand_id = :brandId AND p.deleted_at IS NULL", nativeQuery = true)
    int softDeleteByBrandId(@Param("brandId") Long brandId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM ProductEntity p WHERE p.id IN :ids AND p.deletedAt IS NULL")
    List<ProductEntity> findAllByIdInWithLock(@Param("ids") List<Long> ids);
}
