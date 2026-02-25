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

public interface ProductJpaRepository extends JpaRepository<ProductEntity, Long> {

    Optional<ProductEntity> findByIdAndDeletedAtIsNull(Long id);

    Page<ProductEntity> findAllByDeletedAtIsNull(Pageable pageable);

    Page<ProductEntity> findAllByBrandIdAndDeletedAtIsNull(Long brandId, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM ProductEntity p WHERE p.id IN :ids AND p.deletedAt IS NULL")
    List<ProductEntity> findAllByIdInWithLock(@Param("ids") List<Long> ids);
}
