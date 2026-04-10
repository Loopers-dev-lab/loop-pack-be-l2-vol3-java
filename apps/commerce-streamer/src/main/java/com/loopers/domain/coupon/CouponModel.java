package com.loopers.domain.coupon;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 쿠폰 템플릿 읽기 전용 엔티티 (Streamer 모듈).
 * <p>
 * CAS 발급 카운트 증가를 위한 최소 필드만 포함한다.
 * </p>
 */
@Entity
@Table(name = "coupons")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CouponModel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "coupon_id")
    private Long couponId;

    @Column(name = "max_quantity")
    private Integer maxQuantity;

    @Column(name = "issued_count", nullable = false)
    private int issuedCount = 0;
}
