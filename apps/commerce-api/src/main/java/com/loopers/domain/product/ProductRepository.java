package com.loopers.domain.product;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

public interface ProductRepository {
    Product save(Product product);
    Optional<Product> findById(Long id);
    List<Product> findAllByIdsWithLock(List<Long> ids);
    List<Product> findAll();
    List<Product> findAllByBrandId(Long brandId);

    // 조회 전용 (Brand JOIN)
    List<ProductWithBrand> findAllWithBrand();
    List<ProductWithBrand> findAllWithBrand(String sort);
    List<ProductWithBrand> findAllByBrandIdWithBrand(Long brandId);

    // 페이지네이션 조회 (Brand JOIN)
    Page<ProductWithBrand> findAllWithBrand(String sort, Pageable pageable);
    Page<ProductWithBrand> findAllByBrandIdWithBrand(Long brandId, String sort, Pageable pageable);

    // likeCount atomic 증감 (엔티티 로딩 없이 SQL 직접 실행)
    int incrementLikeCount(Long productId);
    int decrementLikeCount(Long productId);
}
