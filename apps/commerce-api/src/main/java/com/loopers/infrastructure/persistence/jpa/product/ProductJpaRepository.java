package com.loopers.infrastructure.persistence.jpa.product;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * Product JPA Repository.
 * Spring Data JPA를 사용한 영속성 계층.
 */
public interface ProductJpaRepository extends JpaRepository<ProductJpaEntity, Long> {

    Optional<ProductJpaEntity> findByIdAndDeletedAtIsNull(Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM ProductJpaEntity p WHERE p.id = :id AND p.deletedAt IS NULL")
    Optional<ProductJpaEntity> findByIdWithLock(@Param("id") Long id);

    Page<ProductJpaEntity> findAllByDeletedAtIsNull(Pageable pageable);

    Page<ProductJpaEntity> findAllByBrandIdAndDeletedAtIsNull(Long brandId, Pageable pageable);

    List<ProductJpaEntity> findAllByBrandIdAndDeletedAtIsNull(Long brandId);

    List<ProductJpaEntity> findAllByIdIn(List<Long> ids);

    long countByDeletedAtIsNull();

    long countByBrandIdAndDeletedAtIsNull(Long brandId);
}
