package com.loopers.domain.coupon;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;

import java.time.ZonedDateTime;

@Getter
@Entity
@Table(name = "coupon_issue_requests", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"user_id", "coupon_id"})
})
public class CouponIssueRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "coupon_id", nullable = false)
    private Long couponId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status;

    @Column
    private String reason;

    @Column(name = "created_at", nullable = false)
    private ZonedDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private ZonedDateTime updatedAt;

    @Column(name = "deleted_at")
    private ZonedDateTime deletedAt;

    protected CouponIssueRequest() {}

    public boolean isPending() {
        return status == Status.PENDING;
    }

    public void succeed() {
        this.status = Status.SUCCESS;
        this.reason = null;
    }

    public void fail(String reason) {
        this.status = Status.FAILED;
        this.reason = reason;
    }

    public enum Status {
        PENDING, SUCCESS, FAILED
    }
}
