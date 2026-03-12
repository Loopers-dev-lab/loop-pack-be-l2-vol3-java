package com.loopers.infrastructure.persistence.jpa.like;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.ZonedDateTime;

/**
 * 좋아요 JPA 엔티티.
 * Infrastructure Layer에 위치하며 영속성을 담당.
 */
@Entity
@Table(
    name = "likes",
    uniqueConstraints = {
        @UniqueConstraint(columnNames = {"user_id", "product_id"})
    },
    indexes = {
        @Index(name = "idx_likes_product_id", columnList = "product_id")
    }
)
public class LikeJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private ZonedDateTime createdAt;

    protected LikeJpaEntity() {}

    public LikeJpaEntity(Long userId, Long productId) {
        this.userId = userId;
        this.productId = productId;
    }

    @PrePersist
    private void prePersist() {
        this.createdAt = ZonedDateTime.now();
    }

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
