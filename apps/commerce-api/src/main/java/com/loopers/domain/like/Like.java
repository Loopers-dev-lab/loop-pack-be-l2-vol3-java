package com.loopers.domain.like;

import java.time.ZonedDateTime;

/**
 * 좋아요 도메인 엔티티.
 * 사용자-상품 관계를 나타낸다.
 * 순수 Java 객체로 JPA/Spring 의존성 없음.
 */
public class Like {

    private Long id;
    private Long userId;
    private Long productId;
    private ZonedDateTime createdAt;

    private Like() {}

    /**
     * 새 좋아요 생성.
     */
    public static Like create(Long userId, Long productId) {
        Like like = new Like();
        like.userId = userId;
        like.productId = productId;
        like.createdAt = ZonedDateTime.now();
        return like;
    }

    /**
     * DB에서 복원 (Infrastructure에서 사용).
     */
    public static Like reconstitute(Long id, Long userId, Long productId, ZonedDateTime createdAt) {
        Like like = new Like();
        like.id = id;
        like.userId = userId;
        like.productId = productId;
        like.createdAt = createdAt;
        return like;
    }

    // Getters
    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public Long getProductId() {
        return productId;
    }

    public ZonedDateTime getCreatedAt() {
        return createdAt;
    }
}
