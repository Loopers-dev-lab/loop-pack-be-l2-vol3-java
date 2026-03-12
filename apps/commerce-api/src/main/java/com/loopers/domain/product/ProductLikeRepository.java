package com.loopers.domain.product;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface ProductLikeRepository {
    ProductLikeModel save(ProductLikeModel productLike);

    Optional<ProductLikeModel> findByUserIdAndProductId(Long userId, Long productId);

    void delete(ProductLikeModel productLike);

    List<ProductLikeModel> findAllByUserId(Long userId);

    long countByProductId(Long productId);

    Map<Long, Long> countByProductIds(List<Long> productIds);
}
