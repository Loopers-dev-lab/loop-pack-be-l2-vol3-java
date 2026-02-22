package com.loopers.domain.product;

import java.util.List;
import java.util.Optional;

/**
 * 상품 리포지토리 포트 (Domain Layer)
 *
 * 도메인이 인프라(JPA)에 의존하지 않도록 추상화한 인터페이스.
 * 실제 구현은 Infrastructure 계층의 ProductRepositoryImpl이 담당한다.
 */
public interface ProductRepository {
    Product save(Product product);
    Optional<Product> findById(Long id);

    /** 어드민 전체 상품 페이지 조회 (brandId null이면 전체) */
    List<Product> findAll(int page, int size, Long brandId);
    long count(Long brandId);

    /** 고객 노출 가능 상품 페이지 조회 (brandId null이면 전체) */
    List<Product> findAllDisplayable(Long brandId, ProductSortType sort, int page, int size);
    long countDisplayable(Long brandId);

    /** BrandFacade용: 브랜드별 ACTIVE 상품 조회 */
    List<Product> findAllActiveByBrandId(Long brandId);

    /** BrandAdminFacade용: 브랜드별 전체 상품 조회 (삭제 제외) */
    List<Product> findAllByBrandId(Long brandId);

    /** 좋아요 목록용: ID 목록으로 상품 조회 */
    List<Product> findAllByIdIn(List<Long> ids);
}
