package com.loopers.infrastructure.like;

import com.loopers.domain.BaseEntity;
import com.loopers.domain.like.Like;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.util.UUID;

@Entity
@Table(name = "likes", uniqueConstraints = @UniqueConstraint(columnNames = {"member_id", "product_id"}))
public class LikeEntity extends BaseEntity {

    @Column(name = "member_id", nullable = false)
    private String memberId;

    @Column(name = "product_id", nullable = false)
    private UUID productId;

    protected LikeEntity() {
    }

    public LikeEntity(String memberId, UUID productId) {
        this.memberId = memberId;
        this.productId = productId;
    }

    public static LikeEntity from(Like like) {
        return new LikeEntity(like.memberId(), like.productId());
    }

    public Like toDomain() {
        return new Like(memberId, productId);
    }

    public String getMemberId() {
        return memberId;
    }

    public UUID getProductId() {
        return productId;
    }
}
