package com.loopers.infrastructure.persistence.jpa.fcfs;

import com.loopers.domain.fcfs.CouponIssueRequest;

/**
 * CouponIssueRequest 도메인 객체와 JPA 엔티티 간 변환.
 */
public class CouponIssueRequestMapper {

    private CouponIssueRequestMapper() {}

    public static CouponIssueRequest toDomain(CouponIssueRequestJpaEntity entity) {
        if (entity == null) {
            return null;
        }
        return CouponIssueRequest.reconstitute(
            entity.getId(),
            entity.getRequestId(),
            entity.getCouponId(),
            entity.getUserId(),
            entity.getStatus(),
            entity.getFailureReason(),
            entity.getCreatedAt(),
            entity.getUpdatedAt()
        );
    }

    public static CouponIssueRequestJpaEntity toJpaEntity(CouponIssueRequest domain) {
        if (domain == null) {
            return null;
        }
        return new CouponIssueRequestJpaEntity(
            domain.getRequestId(),
            domain.getCouponId(),
            domain.getUserId(),
            domain.getStatus()
        );
    }
}
