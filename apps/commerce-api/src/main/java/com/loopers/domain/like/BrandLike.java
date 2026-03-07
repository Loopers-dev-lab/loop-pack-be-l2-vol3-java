package com.loopers.domain.like;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.LikeErrorType;
import java.time.ZonedDateTime;

/**
 * BrandLike Aggregate Root (순수 POJO)
 * JPA 어노테이션 없음
 */
public class BrandLike {

    private Long id;
    private Long userId;
    private Long brandId;
    private ZonedDateTime createdAt;

    protected BrandLike() {}

    private BrandLike(Long userId, Long brandId) {
        if (userId == null || brandId == null) {
            throw new CoreException(LikeErrorType.INVALID_LIKE_REQUEST);
        }
        this.userId = userId;
        this.brandId = brandId;
        this.createdAt = ZonedDateTime.now();
    }

    /**
     * 새로운 브랜드 좋아요 생성 (비즈니스 로직)
     */
    public static BrandLike of(Long userId, Long brandId) {
        return new BrandLike(userId, brandId);
    }

    /**
     * 영속화된 데이터로부터 도메인 객체 재구성
     */
    public static BrandLike reconstitute(Long id, Long userId, Long brandId, ZonedDateTime createdAt) {
        BrandLike brandLike = new BrandLike();
        brandLike.id = id;
        brandLike.userId = userId;
        brandLike.brandId = brandId;
        brandLike.createdAt = createdAt;
        return brandLike;
    }

    public Long getId() {
        return this.id;
    }

    public Long getUserId() {
        return this.userId;
    }

    public Long getBrandId() {
        return this.brandId;
    }

    public ZonedDateTime getCreatedAt() {
        return this.createdAt;
    }
}
