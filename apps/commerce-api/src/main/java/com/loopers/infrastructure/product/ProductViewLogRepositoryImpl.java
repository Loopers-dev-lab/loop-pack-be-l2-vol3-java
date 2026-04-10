package com.loopers.infrastructure.product;

import com.loopers.domain.product.ProductViewLogModel;
import com.loopers.domain.product.ProductViewLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class ProductViewLogRepositoryImpl implements ProductViewLogRepository {

    private final ProductViewLogJpaRepository productViewLogJpaRepository;

    @Override
    public ProductViewLogModel save(ProductViewLogModel viewLog) {
        return productViewLogJpaRepository.save(viewLog);
    }
}
