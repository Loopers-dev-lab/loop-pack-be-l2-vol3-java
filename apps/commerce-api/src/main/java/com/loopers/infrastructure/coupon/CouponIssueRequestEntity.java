package com.loopers.infrastructure.coupon;

import com.loopers.domain.BaseEntity;
import com.loopers.domain.coupon.CouponIssueRequest;
import com.loopers.domain.coupon.CouponIssueRequestStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Comment;

@Entity
@Table(name = "coupon_issue_requests")
@NoArgsConstructor
public class CouponIssueRequestEntity extends BaseEntity {

    @Comment("유저 id (ref)")
    @Column(name = "ref_user_id", nullable = false, updatable = false)
    private Long refUserId;

    @Comment("쿠폰 템플릿 id (ref)")
    @Column(name = "ref_template_id", nullable = false, updatable = false)
    private Long refTemplateId;

    @Comment("발급 요청 상태")
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private CouponIssueRequestStatus status;

    @Comment("실패 사유")
    @Column(name = "fail_reason")
    private String failReason;

    private CouponIssueRequestEntity(Long refUserId, Long refTemplateId, CouponIssueRequestStatus status) {
        this.refUserId = refUserId;
        this.refTemplateId = refTemplateId;
        this.status = status;
    }

    public static CouponIssueRequestEntity create(CouponIssueRequest request) {
        return new CouponIssueRequestEntity(request.getUserId(), request.getTemplateId(), request.getStatus());
    }

    public CouponIssueRequest toDomain() {
        return CouponIssueRequest.restore(this.getId(), this.refUserId, this.refTemplateId, this.status, this.failReason);
    }
}
