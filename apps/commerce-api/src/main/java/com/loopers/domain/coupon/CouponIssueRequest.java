package com.loopers.domain.coupon;

import com.loopers.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import org.springframework.util.Assert;

import java.util.UUID;

@Getter
@Entity
@Table(name = "coupon_issue_requests", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"coupon_id", "user_id"})
})
public class CouponIssueRequest extends BaseEntity {

    @Column(name = "request_id", nullable = false, unique = true, length = 64)
    private String requestId;

    @Column(name = "coupon_id", nullable = false)
    private Long couponId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private CouponIssueRequestStatus status;

    @Column(name = "reason")
    private String reason;

    protected CouponIssueRequest() {}

    public CouponIssueRequest(Long couponId, Long userId) {
        Assert.notNull(couponId, "couponId는 필수입니다.");
        Assert.notNull(userId, "userId는 필수입니다.");
        this.requestId = UUID.randomUUID().toString();
        this.couponId = couponId;
        this.userId = userId;
        this.status = CouponIssueRequestStatus.PENDING;
    }

    public void markSuccess() {
        this.status = CouponIssueRequestStatus.SUCCESS;
    }

    public void markFailed(String reason) {
        this.status = CouponIssueRequestStatus.FAILED;
        this.reason = reason;
    }
}
