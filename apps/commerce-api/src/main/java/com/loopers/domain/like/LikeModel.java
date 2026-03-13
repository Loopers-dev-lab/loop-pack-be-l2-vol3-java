package com.loopers.domain.like;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 좋아요 JPA 엔티티.
 * 복합 PK(userId + productId)로 사용자당 상품별 1회 좋아요를 보장한다.
 * 등록/취소는 멱등하게 동작한다.
 */
@Entity
@Table(name = "likes")
@IdClass(LikeId.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LikeModel {

    @Id
    @Column(name = "user_id")
    private Long userId;

    @Id
    @Column(name = "product_id")
    private Long productId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    private LikeModel(Long userId, Long productId) {
        validateUserId(userId);
        validateProductId(productId);
        this.userId = userId;
        this.productId = productId;
    }

    /**
     * 좋아요 기록을 생성한다. 복합 PK(userId + productId)로 사용자당 상품별 1회만 가능.
     *
     * @param userId    사용자 ID (필수)
     * @param productId 상품 ID (필수)
     * @return 생성된 LikeModel 인스턴스
     * @throws CoreException userId 또는 productId가 null인 경우 (BAD_REQUEST)
     */
    public static LikeModel create(Long userId, Long productId) {
        return new LikeModel(userId, productId);
    }

    @PrePersist
    private void prePersist() {
        this.createdAt = LocalDateTime.now();
    }

    private static void validateUserId(Long userId) {
        if (userId == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "사용자 ID는 필수입니다.");
        }
    }

    private static void validateProductId(Long productId) {
        if (productId == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "상품 ID는 필수입니다.");
        }
    }
}
