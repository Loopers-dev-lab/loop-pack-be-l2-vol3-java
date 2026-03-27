package com.loopers.infrastructure.coupon.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "coupon_issue_request")
public class CouponIssueRequestStreamerEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long couponTemplateId;

    @Column(nullable = false)
    private Long memberId;

    @Column(nullable = false, length = 20)
    private String status;

    private static final String STATUS_ISSUED = "ISSUED";
    private static final String STATUS_REJECTED = "REJECTED";

    public void markIssued() {
        this.status = STATUS_ISSUED;
    }

    public void markRejected() {
        this.status = STATUS_REJECTED;
    }
}
