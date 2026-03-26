package com.loopers.domain.coupon;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "coupon_issue_request")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CouponIssueRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "request_id", nullable = false)
    private String requestId;

    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "fail_reason")
    private String failReason;

    public void markSuccess() {
        this.status = "SUCCESS";
    }

    public void markFailed(String reason) {
        this.status = "FAILED";
        this.failReason = reason;
    }

    public String requestId() {
        return requestId;
    }

    public String status() {
        return status;
    }
}
