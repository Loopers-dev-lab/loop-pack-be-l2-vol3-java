package com.loopers.infrastructure.product;

import com.loopers.domain.product.ProductRepository;
import org.springframework.stereotype.Component;

@Component
public class ProductRepositoryImpl implements ProductRepository {

    @Override
    public void softDeleteAllByBrandId(Long brandId) {
        // Product 도메인 구현 시 실제 삭제 로직 추가
    }
}
