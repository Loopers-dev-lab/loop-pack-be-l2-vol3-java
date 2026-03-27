package com.loopers.infrastructure.coupon.entity;

import com.loopers.domain.coupon.model.CouponIssueRequest;
import com.loopers.domain.coupon.model.CouponIssueStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "coupon_issue_request")
public class CouponIssueRequestEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long couponTemplateId;

    @Column(nullable = false)
    private Long memberId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CouponIssueStatus status;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    public static CouponIssueRequestEntity toEntity(CouponIssueRequest model) {
        CouponIssueRequestEntity entity = new CouponIssueRequestEntity();
        entity.couponTemplateId = model.getCouponTemplateId();
        entity.memberId = model.getMemberId();
        entity.status = model.getStatus();
        entity.createdAt = model.getCreatedAt();
        return entity;
    }

    public CouponIssueRequest toModel() {
        return CouponIssueRequest.reconstruct(id, couponTemplateId, memberId, status, createdAt);
    }

    public void updateStatus(CouponIssueStatus status) {
        this.status = status;
    }
}
