package com.loopers.domain.like;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.ZonedDateTime;

@Entity
@Table(name = "product_likes")
public class ProductLike {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private ZonedDateTime createdAt;

    protected ProductLike() {}

    private ProductLike(Long userId, Long productId) {
        this.userId = userId;
        this.productId = productId;
    }

    public static ProductLike create(Long userId, Long productId) {
        return new ProductLike(userId, productId);
    }

    @PrePersist
    private void prePersist() {
        this.createdAt = ZonedDateTime.now();
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
