package com.loopers.domain.coupon;

import com.loopers.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;

@Entity
@Table(name = "coupon_issue_requests")
@Getter
public class CouponIssueRequestModel extends BaseEntity {

    @Column(name = "request_id", nullable = false, length = 36)
    private String requestId;

    @Column(name = "ref_coupon_template_id", nullable = false)
    private Long refCouponTemplateId;

    @Column(name = "ref_member_id", nullable = false)
    private Long refMemberId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 10)
    private CouponIssueStatus status;

    protected CouponIssueRequestModel() {}

    public void markAsIssued() {
        this.status = CouponIssueStatus.ISSUED;
    }

    public void markAsRejected() {
        this.status = CouponIssueStatus.REJECTED;
    }
}
