package com.loopers.domain.coupon;

import com.loopers.domain.BaseEntity;
import com.loopers.domain.common.vo.RefMemberId;
import com.loopers.domain.coupon.vo.RefCouponTemplateId;
import com.loopers.infrastructure.jpa.converter.RefCouponTemplateIdConverter;
import com.loopers.infrastructure.jpa.converter.RefMemberIdConverter;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Entity
@Table(
    name = "coupon_issue_requests",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uk_coupon_issue_member_template",
            columnNames = {"ref_coupon_template_id", "ref_member_id"}
        )
    }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CouponIssueRequestModel extends BaseEntity {

    @Column(name = "request_id", nullable = false, unique = true, length = 36)
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

    private CouponIssueRequestModel(Long refCouponTemplateId, Long refMemberId) {
        this.requestId = UUID.randomUUID().toString();
        this.refCouponTemplateId = new RefCouponTemplateId(refCouponTemplateId);
        this.refMemberId = new RefMemberId(refMemberId);
        this.status = CouponIssueStatus.PENDING;
    }

    public static CouponIssueRequestModel create(Long refCouponTemplateId, Long refMemberId) {
        return new CouponIssueRequestModel(refCouponTemplateId, refMemberId);
    }

    public void markAsIssued() {
        this.status = CouponIssueStatus.ISSUED;
    }

    public void markAsRejected() {
        this.status = CouponIssueStatus.REJECTED;
    }
}
