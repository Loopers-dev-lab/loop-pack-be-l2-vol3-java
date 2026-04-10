package com.loopers.infrastructure.product;

import com.loopers.domain.product.ProductViewLogModel;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductViewLogJpaRepository extends JpaRepository<ProductViewLogModel, Long> {
}
