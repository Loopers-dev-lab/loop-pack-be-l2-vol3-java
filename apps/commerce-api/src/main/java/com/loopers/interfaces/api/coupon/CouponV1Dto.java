package com.loopers.interfaces.api.coupon;

import com.loopers.application.coupon.IssuedCouponInfo;

import java.time.ZonedDateTime;
import java.util.List;

import org.springframework.data.domain.Page;

/**
 * 대고객 쿠폰 API 요청/응답 DTO.
 */
public class CouponV1Dto {

    /** 발급 쿠폰 응답 (내 쿠폰 목록·발급 결과) */
    public record IssuedCouponResponse(
        Long id,
        Long couponId,
        String status,
        ZonedDateTime expiredAt,
        ZonedDateTime usedAt,
        ZonedDateTime createdAt
    ) {
        public static IssuedCouponResponse from(IssuedCouponInfo info) {
            if (info == null) {
                return null;
            }
            return new IssuedCouponResponse(
                info.id(),
                info.couponId(),
                info.status(),
                info.expiredAt(),
                info.usedAt(),
                info.createdAt()
            );
        }
    }

    /** 내 쿠폰 목록 페이지 결과 (Jackson 역직렬화를 위해 Page 대신 DTO 사용) */
    public record PagedIssuedCouponsResponse(
        java.util.List<IssuedCouponResponse> content,
        long totalElements,
        int totalPages,
        int number,
        int size
    ) {
        public static PagedIssuedCouponsResponse from(Page<IssuedCouponInfo> page) {
            if (page == null) {
                return new PagedIssuedCouponsResponse(List.of(), 0L, 0, 0, 0);
            }
            java.util.List<IssuedCouponResponse> content = page.getContent().stream()
                .map(IssuedCouponResponse::from)
                .toList();
            return new PagedIssuedCouponsResponse(
                content,
                page.getTotalElements(),
                page.getTotalPages(),
                page.getNumber(),
                page.getSize()
            );
        }
    }
}
