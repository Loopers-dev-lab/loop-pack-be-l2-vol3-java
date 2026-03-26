package com.loopers.domain.product;

import com.loopers.support.page.PageQuery;
import com.loopers.support.page.PagedResult;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * 상품 리포지토리 인터페이스.
 * <p>
 * DIP(의존성 역전 원칙)에 따라 도메인 계층에 정의되며,
 * infrastructure 계층의 {@code ProductRepositoryImpl}이 구현한다.
 * </p>
 */
public interface ProductRepository {

    /**
     * 상품을 저장한다.
     *
     * @param product 저장할 상품 엔티티
     * @return 저장된 상품 엔티티
     */
    ProductModel save(ProductModel product);

    /**
     * 상품 ID로 상품을 조회한다.
     *
     * @param productId 상품 ID
     * @return 상품 엔티티 (존재하지 않으면 빈 Optional)
     */
    Optional<ProductModel> findById(Long productId);

    /**
     * 비관적 쓰기 락(SELECT FOR UPDATE)으로 상품을 조회한다.
     * 동일 상품에 대한 좋아요 연산 직렬화를 위해 사용된다.
     *
     * @param productId 상품 ID
     * @return 상품 엔티티 (존재하지 않으면 빈 Optional)
     */
    Optional<ProductModel> findByIdWithLock(Long productId);

    /**
     * 전체 상품을 조회한다 (삭제된 상품 포함).
     *
     * @return 전체 상품 목록
     */
    List<ProductModel> findAll();

    /**
     * 삭제 여부(del_yn)로 상품을 조회한다.
     *
     * @param delYn 삭제 여부 ("Y" 또는 "N")
     * @return 해당 삭제 상태의 상품 목록
     */
    List<ProductModel> findAllByDelYn(String delYn);

    /**
     * 고객용 상품 목록을 조회한다 (del_yn='N' AND display_status='ACTIVE').
     *
     * @param keyword 검색 키워드 (null이면 전체)
     * @param brandId 브랜드 ID 필터 (null이면 전체)
     * @return 고객 노출 조건을 만족하는 상품 목록
     */
    List<ProductModel> findAllForCustomer(String keyword, Long brandId);

    /**
     * 특정 브랜드에 소속된 상품 목록을 조회한다.
     *
     * @param brandId 브랜드 ID
     * @return 해당 브랜드 소속 상품 목록
     */
    List<ProductModel> findAllByBrandId(Long brandId);

    /**
     * 상품 ID 목록으로 상품을 일괄 조회한다.
     *
     * @param productIds 상품 ID 목록
     * @return 해당 상품 목록
     */
    List<ProductModel> findAllByProductIds(Collection<Long> productIds);

    /**
     * 고객용 상품 목록을 페이징하여 조회한다.
     *
     * @param keyword 검색 키워드 (null이면 전체)
     * @param brandId 브랜드 ID 필터 (null이면 전체)
     * @param query   페이징/정렬 요청 정보
     * @return 페이징된 상품 목록
     */
    PagedResult<ProductModel> findAllForCustomer(String keyword, Long brandId, PageQuery query);

    /**
     * 상품의 좋아요 수를 1 증가시킨다.
     *
     * @param productId 상품 ID
     */
    void incrementLikeCount(Long productId);

    /**
     * 상품의 좋아요 수를 1 감소시킨다 (최솟값 0 보장).
     *
     * @param productId 상품 ID
     */
    void decrementLikeCount(Long productId);

    /**
     * 상품의 like_count와 likes 테이블의 실제 좋아요 수가 불일치하는 상품 목록을 조회한다.
     *
     * @return 불일치 감지 결과 목록
     */
    List<LikeCountMismatch> findLikeCountMismatches();

    /**
     * 상품의 like_count를 지정된 값으로 갱신한다.
     *
     * @param productId 상품 ID
     * @param likeCount 갱신할 좋아요 수
     */
    void updateLikeCount(Long productId, long likeCount);
}
