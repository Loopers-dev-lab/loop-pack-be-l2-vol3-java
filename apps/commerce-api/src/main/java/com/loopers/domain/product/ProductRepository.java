package com.loopers.domain.product;

import java.util.Optional;

/**
 * 상품 영속성 인터페이스.
 * 구현체는 infrastructure 레이어에 둔다.
 */
public interface ProductRepository {

    Optional<ProductModel> findById(Long id);

    Optional<ProductModel> findByIdAndNotDeleted(Long id);

    ProductModel save(ProductModel product);
}
