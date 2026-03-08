package com.loopers.domain.product;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;

/**
 * 상품 도메인 리포지토리 인터페이스.
 */
public interface ProductRepository {

    /**
     * 상품을 저장한다.
     *
     * @param product 저장할 상품
     * @return 저장된 상품
     */
    Product save(Product product);

    /**
     * ID로 상품을 조회한다. 삭제 여부와 무관하게 조회한다.
     *
     * @param productId 상품 ID
     * @return 상품 (존재하지 않으면 빈 Optional)
     */
    Optional<Product> findById(Long productId);

    /**
     * ID로 활성 상품을 조회한다.
     *
     * @param productId 상품 ID
     * @return 활성 상품 (존재하지 않거나 삭제된 경우 빈 Optional)
     */
    Optional<Product> findByIdAndDeletedAtIsNull(Long productId);

    /**
     * ID로 활성 상품을 비관적 락과 함께 조회한다.
     *
     * <p>재고 차감 등 동시성 제어가 필요한 경우 사용한다.</p>
     *
     * @param productId 상품 ID
     * @return 활성 상품 (존재하지 않거나 삭제된 경우 빈 Optional)
     */
    Optional<Product> findByIdAndDeletedAtIsNullForUpdate(Long productId);

    /**
     * ID 목록에 해당하는 활성 상품을 모두 조회한다.
     *
     * @param productIds 상품 ID 목록
     * @return 활성 상품 목록
     */
    List<Product> findAllByIdInAndDeletedAtIsNull(List<Long> productIds);

    /**
     * 전체 또는 특정 브랜드의 상품을 페이징 조회한다.
     *
     * @param brandId  브랜드 ID (null이면 전체 조회)
     * @param pageable 페이징 조건
     * @return 상품 슬라이스
     */
    Slice<Product> findAll(Long brandId, Pageable pageable);

    /**
     * 활성 상품을 정렬 조건과 함께 페이징 조회한다.
     *
     * @param brandId  브랜드 ID (null이면 전체 조회)
     * @param sortType 정렬 타입
     * @param pageable 페이징 조건
     * @return 활성 상품 슬라이스
     */
    Slice<Product> findAllActiveProducts(Long brandId, ProductSortType sortType, Pageable pageable);

    /**
     * 특정 브랜드의 활성 상품을 모두 조회한다.
     *
     * @param brandId 브랜드 ID
     * @return 활성 상품 목록
     */
    List<Product> findAllByBrandIdAndDeletedAtIsNull(Long brandId);

    /**
     * 특정 브랜드의 모든 상품을 소프트 삭제한다.
     *
     * @param brandId 브랜드 ID
     */
    void softDeleteAllByBrandId(Long brandId);

    /**
     * 활성 상품 존재 여부를 확인한다.
     *
     * @param productId 상품 ID
     * @return 활성 상품이 존재하면 true
     */
    boolean existsByIdAndDeletedAtIsNull(Long productId);

    /**
     * 활성 상품의 좋아요 수를 1 증가시킨다 (아토믹 업데이트).
     *
     * @param productId 상품 ID
     * @return 업데이트된 행 수 (0이면 상품이 존재하지 않거나 삭제됨)
     */
    int incrementLikeCount(Long productId);

    /**
     * 활성 상품의 좋아요 수를 1 감소시킨다 (아토믹 업데이트).
     *
     * @param productId 상품 ID
     * @return 업데이트된 행 수 (0이면 상품이 존재하지 않거나, 삭제됐거나, likeCount가 이미 0)
     */
    int decrementLikeCount(Long productId);
}
