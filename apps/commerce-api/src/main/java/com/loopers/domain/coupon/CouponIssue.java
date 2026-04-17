package com.loopers.domain.coupon;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.ZonedDateTime;

@Entity
@Table(name = "coupon_issue", indexes = {
    @Index(name = "idx_coupon_issue_member_id", columnList = "member_id"),
    @Index(name = "idx_coupon_issue_coupon_id", columnList = "coupon_id")
}, uniqueConstraints = {
    @UniqueConstraint(name = "uk_coupon_issue_coupon_member", columnNames = {"coupon_id", "member_id"})
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CouponIssue {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "coupon_id", nullable = false)
    private Long couponId;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Column(name = "used_order_id")
    private Long usedOrderId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CouponIssueStatus status;

    @Column(name = "expired_at", nullable = false)
    private ZonedDateTime expiredAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private ZonedDateTime createdAt;

    public CouponIssue(Long couponId, Long memberId, ZonedDateTime expiredAt) {
        this.couponId = couponId;
        this.memberId = memberId;
        this.status = CouponIssueStatus.AVAILABLE;
        this.expiredAt = expiredAt;
        this.createdAt = ZonedDateTime.now();
    }

    public void use(Long orderId, ZonedDateTime now) {
        if (this.status != CouponIssueStatus.AVAILABLE) {
            throw new CoreException(ErrorType.BAD_REQUEST, "사용할 수 없는 쿠폰입니다.");
        }
        if (isExpired(now)) {
            throw new CoreException(ErrorType.BAD_REQUEST, "만료된 쿠폰입니다.");
        }
        this.status = CouponIssueStatus.USED;
        this.usedOrderId = orderId;
    }

    public void linkOrder(Long orderId) {
        this.usedOrderId = orderId;
    }

    public void cancelUse(ZonedDateTime now) {
        if (this.status != CouponIssueStatus.USED) {
            throw new CoreException(ErrorType.BAD_REQUEST, "사용된 쿠폰만 복원할 수 있습니다.");
        }
        this.status = isExpired(now) ? CouponIssueStatus.EXPIRED : CouponIssueStatus.AVAILABLE;
        this.usedOrderId = null;
    }

    public boolean isExpired(ZonedDateTime now) {
        return now.isAfter(expiredAt);
    }

    public CouponIssueStatus getEffectiveStatus(ZonedDateTime now) {
        if (this.status == CouponIssueStatus.AVAILABLE && isExpired(now)) {
            return CouponIssueStatus.EXPIRED;
        }
        return this.status;
    }
}
