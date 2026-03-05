package com.loopers.domain.like;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.ZonedDateTime;

/**
 * 좋아요 엔티티. hard delete 정책이므로 BaseEntity를 상속하지 않는다.
 * 생성 후 수정이 없으므로 updatedAt, deletedAt 불필요.
 */
@Entity
@Table(name = "likes",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_likes_user_product",
                columnNames = {"user_id", "product_id"}
        ))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
public class Like {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 좋아요를 누른 회원 ID
    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    // 좋아요 대상 상품 ID
    @Column(name = "product_id", nullable = false, updatable = false)
    private Long productId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private ZonedDateTime createdAt;

    public Like(Long userId, Long productId) {
        if (userId == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "userId는 필수입니다.");
        }
        if (productId == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "productId는 필수입니다.");
        }
        this.userId = userId;
        this.productId = productId;
    }

    @PrePersist
    private void prePersist() {
        this.createdAt = ZonedDateTime.now();
    }
}
