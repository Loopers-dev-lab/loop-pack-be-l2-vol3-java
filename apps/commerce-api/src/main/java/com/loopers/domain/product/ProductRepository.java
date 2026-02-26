package com.loopers.domain.product;

import java.util.List;
import java.util.Optional;

/**
 * 상품 영속성 인터페이스.
 * 구현체는 infrastructure 레이어에 둔다.
 */
public interface ProductRepository {

    Optional<ProductModel> findById(Long id);

    Optional<ProductModel> findByIdAndNotDeleted(Long id);

    /**
     * 해당 브랜드에 속한 미삭제 상품 목록을 조회한다.
     * 브랜드 연쇄 삭제 시 사용.
     */
    List<ProductModel> findByBrandIdAndNotDeleted(Long brandId);

    /**
     * 재고 갱신 시 비관적 락으로 조회한다. (04-erd §0)
     */
    Optional<ProductModel> findByIdForUpdate(Long id);

    ProductModel save(ProductModel product);
}
