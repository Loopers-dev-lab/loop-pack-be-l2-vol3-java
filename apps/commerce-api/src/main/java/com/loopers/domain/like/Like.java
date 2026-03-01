package com.loopers.domain.like;

import com.loopers.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;

@Entity
@Table(
    name = "likes",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_like_user_product", columnNames = {"user_id", "product_id"})
    }
)
@Getter
public class Like extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    protected Like() {
    }

    private Like(Long userId, Long productId) {
        this.userId = userId;
        this.productId = productId;
    }

    public static Like create(Long userId, Long productId) {
        if (userId == null || userId <= 0) {
            throw new IllegalArgumentException("사용자 ID는 필수입니다");
        }
        if (productId == null || productId <= 0) {
            throw new IllegalArgumentException("상품 ID는 필수입니다");
        }
        return new Like(userId, productId);
    }

    public boolean isDeleted() {
        return getDeletedAt() != null;
    }

    @Override
    protected void guard() {
        if (this.userId == null || this.userId <= 0) {
            throw new IllegalArgumentException("사용자 ID는 필수입니다");
        }
        if (this.productId == null || this.productId <= 0) {
            throw new IllegalArgumentException("상품 ID는 필수입니다");
        }
    }
}
