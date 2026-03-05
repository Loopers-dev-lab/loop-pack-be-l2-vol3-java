package com.loopers.interfaces.api.coupon;

import com.loopers.application.coupon.CouponInfo;
import com.loopers.application.coupon.UserCouponInfo;
import com.loopers.domain.coupon.CouponStatus;
import com.loopers.domain.coupon.CouponType;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.List;

public class CouponV1Dto {

    public record CreateRequest(
            String name,
            CouponType type,
            BigDecimal value,
            BigDecimal minOrderAmount,
            ZonedDateTime expiredAt
    ) {}

    public record UpdateRequest(
            String name,
            CouponType type,
            BigDecimal value,
            BigDecimal minOrderAmount,
            ZonedDateTime expiredAt
    ) {}

    public record Response(
            Long id,
            String name,
            CouponType type,
            BigDecimal value,
            BigDecimal minOrderAmount,
            ZonedDateTime expiredAt,
            boolean expired,
            ZonedDateTime createdAt,
            ZonedDateTime updatedAt
    ) {
        public static Response from(CouponInfo info) {
            return new Response(
                    info.id(),
                    info.name(),
                    info.type(),
                    info.value(),
                    info.minOrderAmount(),
                    info.expiredAt(),
                    info.expired(),
                    info.createdAt(),
                    info.updatedAt()
            );
        }
    }

    public record UserCouponResponse(
            Long id,
            Long userId,
            Long couponId,
            CouponStatus status,
            ZonedDateTime createdAt,
            ZonedDateTime updatedAt
    ) {
        public static UserCouponResponse from(UserCouponInfo info) {
            return new UserCouponResponse(
                    info.id(),
                    info.userId(),
                    info.couponId(),
                    info.status(),
                    info.createdAt(),
                    info.updatedAt()
            );
        }
    }

    public record PageResponse(
            List<Response> content,
            int number,
            int size,
            long totalElements,
            int totalPages
    ) {
        public static PageResponse from(org.springframework.data.domain.Page<CouponInfo> page) {
            List<Response> content = page.getContent().stream()
                    .map(Response::from)
                    .toList();
            return new PageResponse(content, page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
        }
    }

    public record UserCouponPageResponse(
            List<UserCouponResponse> content,
            int number,
            int size,
            long totalElements,
            int totalPages
    ) {
        public static UserCouponPageResponse from(org.springframework.data.domain.Page<UserCouponInfo> page) {
            List<UserCouponResponse> content = page.getContent().stream()
                    .map(UserCouponResponse::from)
                    .toList();
            return new UserCouponPageResponse(content, page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
        }
    }
}
