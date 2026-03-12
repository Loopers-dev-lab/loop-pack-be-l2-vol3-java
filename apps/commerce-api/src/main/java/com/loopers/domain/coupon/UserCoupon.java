package com.loopers.domain.coupon;

import com.loopers.domain.BaseEntity;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 유저에게 발급된 쿠폰 엔티티.
 * soft delete 정책으로 이력을 보존한다.
 * BaseEntity를 상속하여 createdAt/updatedAt/deletedAt을 자동 관리한다.
 *
 * UNIQUE(user_id, coupon_template_id) 제약은 활성 레코드(deleted_at IS NULL) 기준으로
 * 애플리케이션 레벨에서 검증한다. (soft delete 테이블이므로 DB UNIQUE 미적용)
 */
@Entity
@Table(name = "user_coupons")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
public class UserCoupon extends BaseEntity {

    @Column(name = "coupon_template_id", nullable = false, updatable = false)
    private Long couponTemplateId;

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    // 발급 시점의 만료일 스냅샷. 템플릿 변경에 영향받지 않도록 저장한다.
    @Column(name = "expired_at", nullable = false, updatable = false)
    private LocalDateTime expiredAt;

    // 사용 시각. 미사용 시 null
    @Column(name = "used_at")
    private LocalDateTime usedAt;

    public UserCoupon(Long couponTemplateId, Long userId, LocalDateTime expiredAt) {
        if (couponTemplateId == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "쿠폰 템플릿 ID는 필수입니다.");
        }
        if (userId == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "유저 ID는 필수입니다.");
        }
        if (expiredAt == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "만료일은 필수입니다.");
        }
        this.couponTemplateId = couponTemplateId;
        this.userId = userId;
        this.expiredAt = expiredAt;
    }

    // 만료 검증 — 발급 시점에 스냅샷된 만료일 기준 (BR-C04)
    public void validateNotExpired(LocalDateTime now) {
        if (now.isAfter(expiredAt)) {
            throw new CoreException(ErrorType.BAD_REQUEST, "만료된 쿠폰입니다.");
        }
    }

}
