package com.loopers.infrastructure.like;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.ZonedDateTime;

/**
 * BrandLikeEntity (JPA 전용)
 * JPA 어노테이션만 사용
 */
@Entity
@Table(name = "brand_likes", uniqueConstraints = {
        @UniqueConstraint(name = "uk_brand_likes", columnNames = {"user_id", "brand_id"})
})
public class BrandLikeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "brand_id", nullable = false)
    private Long brandId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private ZonedDateTime createdAt;

    protected BrandLikeEntity() {}

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public Long getBrandId() {
        return brandId;
    }

    public void setBrandId(Long brandId) {
        this.brandId = brandId;
    }

    public ZonedDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(ZonedDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
