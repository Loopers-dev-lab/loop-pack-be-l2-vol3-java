package com.loopers.domain.product;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * 상품 영속성 인터페이스.
 * 구현체는 infrastructure 레이어에 둔다.
 */
public interface ProductRepository {

    Optional<ProductModel> findById(Long id);

    Optional<ProductModel> findByIdAndNotDeleted(Long id);

    /**
     * 미삭제 상품 목록을 정렬·페이징하여 조회한다.
     * @param sortOrder 정렬 기준 (LATEST, PRICE_ASC, PRICE_DESC, LIKES_DESC)
     * @param brandId   null이면 전체, 값이 있으면 해당 브랜드만
     */
    Page<ProductModel> findNotDeleted(ProductSortOrder sortOrder, Long brandId, Pageable pageable);

    /**
     * 해당 브랜드에 속한 미삭제 상품 목록을 조회한다.
     * 브랜드 연쇄 삭제 시 사용.
     */
    List<ProductModel> findByBrandIdAndNotDeleted(Long brandId);

    /**
     * 재고 갱신 시 비관적 락으로 조회한다. (04-erd §0)
     */
    Optional<ProductModel> findByIdForUpdate(Long id);

    /**
     * 해당 브랜드에 속한 모든 미삭제 상품을 한 번에 soft delete한다.
     * 브랜드 삭제 시 연쇄 삭제에 사용하며, 루프 없이 단일 UPDATE로 부하를 줄인다.
     */
    void softDeleteByBrandIdBulk(Long brandId);

    ProductModel save(ProductModel product);
}
