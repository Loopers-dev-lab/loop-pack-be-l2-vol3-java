package com.loopers.infrastructure.product;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ProductJpaRepository extends JpaRepository<ProductEntity, Long> {

    Optional<ProductEntity> findByIdAndDeletedAtIsNull(Long id);

    Page<ProductEntity> findAllByDeletedAtIsNull(Pageable pageable);

    Page<ProductEntity> findAllByBrandIdAndDeletedAtIsNull(Long brandId, Pageable pageable);
}
