package com.loopers.infrastructure.product;

import com.loopers.domain.product.Product;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProductJpaRepository extends JpaRepository<Product, Long> {
    List<Product> findAllByBrandIdAndDeletedAtIsNull(Long brandId);
    List<Product> findAllByIdInAndDeletedAtIsNull(List<Long> ids);
}
