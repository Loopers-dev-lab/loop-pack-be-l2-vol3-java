package com.loopers.domain.coupon;

import com.loopers.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;

@Getter
@Entity
@Table(name = "coupon_issue_requests", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"user_id", "coupon_id"})
})
public class CouponIssueRequest extends BaseEntity {

    @Column(name = "coupon_id", nullable = false)
    private Long couponId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status;

    @Column
    private String reason;

    protected CouponIssueRequest() {}

    private CouponIssueRequest(Long couponId, Long userId) {
        this.couponId = couponId;
        this.userId = userId;
        this.status = Status.PENDING;
    }

    public static CouponIssueRequest create(Long couponId, Long userId) {
        return new CouponIssueRequest(couponId, userId);
    }

    public void succeed() {
        this.status = Status.SUCCESS;
    }

    public void fail(String reason) {
        this.status = Status.FAILED;
        this.reason = reason;
    }

    public enum Status {
        PENDING, SUCCESS, FAILED
    }
}
