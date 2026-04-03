package com.loopers.domain.coupon;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.time.ZonedDateTime;

/**
 * Consumer용 UserCoupon 엔티티 (발급 INSERT 전용).
 * commerce-api의 UserCoupon과 같은 테이블을 바라본다.
 */
@Entity
@Table(name = "user_coupons")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
public class UserCoupon {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "coupon_template_id", nullable = false, updatable = false)
    private Long couponTemplateId;

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    @Column(name = "expired_at", nullable = false, updatable = false)
    private LocalDateTime expiredAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private ZonedDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private ZonedDateTime updatedAt;

    @Column(name = "deleted_at")
    private ZonedDateTime deletedAt;

    public UserCoupon(Long couponTemplateId, Long userId, LocalDateTime expiredAt) {
        this.couponTemplateId = couponTemplateId;
        this.userId = userId;
        this.expiredAt = expiredAt;
    }

    @PrePersist
    private void prePersist() {
        ZonedDateTime now = ZonedDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }
}
