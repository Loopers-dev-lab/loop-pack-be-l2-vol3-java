package com.loopers.domain.like;

import com.loopers.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

import static lombok.AccessLevel.PROTECTED;

/**
 * 좋아요 도메인 엔티티.
 * 1인 1상품 1좋아요. Hard delete(물리 삭제) 사용.
 */
@Entity
@Table(name = "likes")
@Getter
@NoArgsConstructor(access = PROTECTED)
public class LikeModel extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    private LikeModel(Long userId, Long productId) {
        this.userId = userId;
        this.productId = productId;
    }

    /**
     * 유효한 userId, productId로 좋아요를 생성한다.
     *
     * @param userId    User 엔티티 PK (not null)
     * @param productId Product 엔티티 PK (not null)
     * @return 생성된 LikeModel
     */
    public static LikeModel create(Long userId, Long productId) {
        if (userId == null) {
            throw new IllegalArgumentException("사용자 ID는 null일 수 없습니다.");
        }
        if (productId == null) {
            throw new IllegalArgumentException("상품 ID는 null일 수 없습니다.");
        }
        return new LikeModel(userId, productId);
    }
}
