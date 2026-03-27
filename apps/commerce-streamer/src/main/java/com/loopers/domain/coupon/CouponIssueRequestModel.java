package com.loopers.domain.coupon;

import com.loopers.domain.BaseEntity;
import com.loopers.domain.common.vo.RefMemberId;
import com.loopers.domain.coupon.vo.RefCouponTemplateId;
import com.loopers.infrastructure.jpa.converter.RefCouponTemplateIdConverter;
import com.loopers.infrastructure.jpa.converter.RefMemberIdConverter;
import jakarta.persistence.*;
import lombok.Getter;

@Entity
@Table(name = "coupon_issue_requests")
@Getter
public class CouponIssueRequestModel extends BaseEntity {

    @Column(name = "request_id", nullable = false, length = 36)
    private String requestId;

    @Convert(converter = RefCouponTemplateIdConverter.class)
    @Column(name = "ref_coupon_template_id", nullable = false)
    private RefCouponTemplateId refCouponTemplateId;

    @Convert(converter = RefMemberIdConverter.class)
    @Column(name = "ref_member_id", nullable = false)
    private RefMemberId refMemberId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 10)
    private CouponIssueStatus status;

    protected CouponIssueRequestModel() {}

    public void markAsIssued() {
        if (this.status != CouponIssueStatus.PENDING) {
            throw new IllegalStateException("PENDING 상태에서만 ISSUED로 전이 가능합니다: " + this.status);
        }
        this.status = CouponIssueStatus.ISSUED;
    }

    public void markAsRejected() {
        if (this.status != CouponIssueStatus.PENDING) {
            throw new IllegalStateException("PENDING 상태에서만 REJECTED로 전이 가능합니다: " + this.status);
        }
        this.status = CouponIssueStatus.REJECTED;
    }
}
