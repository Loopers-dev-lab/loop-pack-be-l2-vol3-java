package com.loopers.domain.coupon;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 사용자 발급 쿠폰 최소 엔티티 (Streamer 모듈).
 * <p>
 * 선착순 쿠폰 발급 Consumer에서 사용자 쿠폰을 생성하기 위한 최소 필드만 포함한다.
 * </p>
 */
@Entity
@Table(name = "user_coupons",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_user_coupons",
                columnNames = {"user_id", "coupon_id"}
        ))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserCouponModel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_coupon_id")
    private Long userCouponId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "coupon_id", nullable = false)
    private Long couponId;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "issued_at", nullable = false, updatable = false)
    private LocalDateTime issuedAt;

    private UserCouponModel(Long userId, Long couponId) {
        this.userId = userId;
        this.couponId = couponId;
        this.status = "AVAILABLE";
        this.issuedAt = LocalDateTime.now();
    }

    /**
     * 사용자 발급 쿠폰을 생성한다.
     */
    public static UserCouponModel create(Long userId, Long couponId) {
        return new UserCouponModel(userId, couponId);
    }

    @PrePersist
    private void prePersist() {
        if (this.issuedAt == null) {
            this.issuedAt = LocalDateTime.now();
        }
    }
}
