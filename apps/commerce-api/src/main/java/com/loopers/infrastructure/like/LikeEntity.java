package com.loopers.infrastructure.like;

import com.loopers.domain.BaseEntity;
import com.loopers.domain.like.Like;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Comment;

/**
 *  좋아요 DB 엔티티
 */
@Entity
@Table(name = "likes")
@NoArgsConstructor
public class LikeEntity extends BaseEntity {

    @Comment("상품 id (ref)")
    @Column(name = "ref_product_id", nullable = false, updatable = false)
    private Long refProductId;

    @Comment("유저 id (ref)")
    @Column(name = "ref_user_id", nullable = false, updatable = false)
    private Long refUserId;

    public LikeEntity(Like like) {
        this.refProductId = like.getRefProductId();
        this.refUserId = like.getRefUserId();
    }

    public static LikeEntity toEntity(Like like) {
        return new LikeEntity(like);
    }

    public Long getRefProductId() {
        return refProductId;
    }

    public Long getRefUserId() {
        return refUserId;
    }

    public static Like toDomain(LikeEntity likeEntity) {
        return Like.create(
            likeEntity.getId(),
            likeEntity.getRefProductId(),
            likeEntity.getRefUserId()
        );
    }
}
