package com.loopers.domain.like;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.ZonedDateTime;

@Entity
@Table(name = "brand_likes", uniqueConstraints = {
        @UniqueConstraint(name = "uk_brand_likes", columnNames = {"user_id", "brand_id"})
})
public class BrandLike {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "brand_id", nullable = false)
    private Long brandId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private ZonedDateTime createdAt;

    protected BrandLike() {}

    private BrandLike(Long userId, Long brandId) {
        this.userId = userId;
        this.brandId = brandId;
    }

    public static BrandLike create(Long userId, Long brandId) {
        return new BrandLike(userId, brandId);
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

    public Long getBrandId() {
        return this.brandId;
    }

    public ZonedDateTime getCreatedAt() {
        return this.createdAt;
    }
}
