package com.loopers.domain.coupon;

import com.loopers.support.enums.UserCouponStatus;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
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
 * 사용자 발급 쿠폰 JPA 엔티티.
 * <p>
 * 발급 이력을 관리하며, 상태(AVAILABLE/USED/EXPIRED)로 생명주기를 관리한다.
 * 소프트 삭제 대신 상태 관리를 사용하므로 {@code BaseStringIdEntity}를 상속하지 않는다.
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

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserCouponStatus status;

    @Column(name = "issued_at", nullable = false, updatable = false)
    private LocalDateTime issuedAt;

    @Column(name = "used_at")
    private LocalDateTime usedAt;

    @Column(name = "order_id")
    private Long orderId;

    private UserCouponModel(Long userId, Long couponId) {
        this.userId = userId;
        this.couponId = couponId;
        this.status = UserCouponStatus.AVAILABLE;
        this.issuedAt = LocalDateTime.now();
    }

    /**
     * 사용자 발급 쿠폰을 생성한다. 상태는 AVAILABLE, 발급 일시는 현재 시각으로 초기화된다.
     */
    public static UserCouponModel create(Long userId, Long couponId) {
        return new UserCouponModel(userId, couponId);
    }

    /**
     * 쿠폰을 사용 완료 처리한다.
     *
     * @param orderId 사용된 주문 ID
     * @throws CoreException 이미 사용된 쿠폰인 경우 COUPON_NOT_AVAILABLE
     */
    public void markAsUsed(Long orderId) {
        if (this.status != UserCouponStatus.AVAILABLE) {
            throw new CoreException(ErrorType.COUPON_NOT_AVAILABLE);
        }
        this.status = UserCouponStatus.USED;
        this.usedAt = LocalDateTime.now();
        this.orderId = orderId;
    }

    /**
     * 쿠폰 사용 가능 여부를 반환한다.
     */
    public boolean isAvailable() {
        return this.status == UserCouponStatus.AVAILABLE;
    }

    /**
     * 사용된 쿠폰을 사용 가능 상태로 복원한다 (멱등).
     * 이미 AVAILABLE이면 무시한다.
     */
    public void restoreToAvailable() {
        if (this.status == UserCouponStatus.AVAILABLE) {
            return;
        }
        this.status = UserCouponStatus.AVAILABLE;
        this.usedAt = null;
        this.orderId = null;
    }

    @PrePersist
    private void prePersist() {
        if (this.issuedAt == null) {
            this.issuedAt = LocalDateTime.now();
        }
    }
}
