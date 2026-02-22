package com.loopers.infrastructure.like;

import com.loopers.domain.BaseEntity;
import com.loopers.domain.like.Like;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "likes", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"user_id", "product_id"})
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LikeJpaEntity extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    private LikeJpaEntity(Long userId, Long productId) {
        this.userId = userId;
        this.productId = productId;
    }

    public static LikeJpaEntity from(Like like) {
        return new LikeJpaEntity(like.getUserId(), like.getProductId());
    }

    public Like toDomain() {
        return Like.of(getId(), userId, productId);
    }
}
