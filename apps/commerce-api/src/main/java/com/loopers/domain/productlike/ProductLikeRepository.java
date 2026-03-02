package com.loopers.domain.productlike;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

public interface ProductLikeRepository {
    ProductLike save(ProductLike productLike);
    Optional<ProductLike> findByUserIdAndProductId(Long userId, Long productId);
    boolean existsByUserIdAndProductId(Long userId, Long productId);
    void delete(ProductLike productLike);
    void deleteByProductId(Long productId);
    Page<ProductLike> findAllByUserId(Long userId, Pageable pageable);
    List<ProductLike> findAllByProductId(Long productId);
    void deleteByProductIds(List<Long> productIds);
}
