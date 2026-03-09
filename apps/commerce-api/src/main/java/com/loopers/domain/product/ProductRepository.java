package com.loopers.domain.product;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

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
    Optional<ProductModel> findById(String productId);

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
    List<ProductModel> findAllForCustomer(String keyword, String brandId);

    /**
     * 특정 브랜드에 소속된 상품 목록을 조회한다.
     *
     * @param brandId 브랜드 ID
     * @return 해당 브랜드 소속 상품 목록
     */
    List<ProductModel> findAllByBrandId(String brandId);

    /**
     * 상품 ID 목록으로 상품을 일괄 조회한다.
     *
     * @param productIds 상품 ID 목록
     * @return 해당 상품 목록
     */
    List<ProductModel> findAllByProductIds(Collection<String> productIds);

    /**
     * 고객용 상품 목록을 페이징하여 조회한다.
     *
     * @param keyword  검색 키워드 (null이면 전체)
     * @param brandId  브랜드 ID 필터 (null이면 전체)
     * @param pageable 페이징/정렬 정보
     * @return 페이징된 상품 목록
     */
    Page<ProductModel> findAllForCustomer(String keyword, String brandId, Pageable pageable);
}
