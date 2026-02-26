package com.loopers.domain.like;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.LikeErrorType;
import java.time.ZonedDateTime;

/**
 * ProductLike Aggregate Root (순수 POJO)
 * JPA 어노테이션 없음
 */
public class ProductLike {

    private Long id;
    private Long userId;
    private Long productId;
    private ZonedDateTime createdAt;

    protected ProductLike() {}

    private ProductLike(Long userId, Long productId) {
        if (userId == null || productId == null) {
            throw new CoreException(LikeErrorType.INVALID_LIKE_REQUEST);
        }
        this.userId = userId;
        this.productId = productId;
        this.createdAt = ZonedDateTime.now();
    }

    /**
     * 새로운 상품 좋아요 생성 (비즈니스 로직)
     */
    public static ProductLike create(Long userId, Long productId) {
        return new ProductLike(userId, productId);
    }

    /**
     * 영속화된 데이터로부터 도메인 객체 재구성
     */
    public static ProductLike reconstitute(Long id, Long userId, Long productId, ZonedDateTime createdAt) {
        ProductLike productLike = new ProductLike();
        productLike.id = id;
        productLike.userId = userId;
        productLike.productId = productId;
        productLike.createdAt = createdAt;
        return productLike;
    }

    public Long getId() {
        return this.id;
    }

    public Long getUserId() {
        return this.userId;
    }

    public Long getProductId() {
        return this.productId;
    }

    public ZonedDateTime getCreatedAt() {
        return this.createdAt;
    }
}
