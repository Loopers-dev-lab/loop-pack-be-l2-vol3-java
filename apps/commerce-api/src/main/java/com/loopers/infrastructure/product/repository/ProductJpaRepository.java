package com.loopers.infrastructure.product.repository;

import com.loopers.infrastructure.product.entity.ProductEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProductJpaRepository extends JpaRepository<ProductEntity, Long> {

    Page<ProductEntity> findAllByBrandId(Long brandId, Pageable pageable);

    List<ProductEntity> findAllByIdIn(List<Long> ids);

    void deleteAllByBrandId(Long brandId);
}
