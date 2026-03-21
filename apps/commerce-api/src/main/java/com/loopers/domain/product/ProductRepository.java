package com.loopers.domain.product;

import com.loopers.domain.common.cursor.CursorPageResult;
import com.loopers.domain.product.vo.ProductId;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

public interface ProductRepository {
    ProductModel save(ProductModel product);

    Optional<ProductModel> findByProductId(ProductId productId);

    boolean existsByProductId(ProductId productId);

    Page<ProductModel> findProducts(Long refBrandId, String sortBy, Pageable pageable);

    CursorPageResult<ProductModel> findProductsByCursor(Long refBrandId, String sortBy, String cursor, int size);

    /**
     * 재고를 차감합니다. 재고가 부족하면 false를 반환합니다.
     * 동시성 제어를 위해 조건부 UPDATE를 사용합니다.
     */
    boolean decreaseStockIfAvailable(Long productId, int quantity);

    /**
     * 재고를 증가시킵니다.
     */
    void increaseStock(Long productId, int quantity);

    /**
     * 브랜드 DB PK로 삭제되지 않은 상품 목록을 조회합니다.
     */
    List<ProductModel> findByRefBrandId(Long brandId);

    /**
     * DB PK로 상품을 조회합니다.
     */
    Optional<ProductModel> findById(Long id);
}
