package com.loopers.interfaces.api.admin;

import com.loopers.application.coupon.CouponInfo;
import com.loopers.application.coupon.UserCouponInfo;
import java.time.ZonedDateTime;
import java.util.List;
import org.springframework.data.domain.Page;

public class AdminCouponV1Dto {

    public record CreateCouponRequest(
        String name,
        String type,
        Integer value,
        Integer minOrderAmount,
        ZonedDateTime expiredAt,
        Integer maxIssuable
    ) {}

    public record UpdateCouponRequest(
        String name,
        String type,
        Integer value,
        Integer minOrderAmount,
        ZonedDateTime expiredAt
    ) {}

    public record AdminCouponResponse(
        Long couponId,
        String name,
        String type,
        int value,
        int minOrderAmount,
        ZonedDateTime expiredAt,
        ZonedDateTime createdAt,
        ZonedDateTime updatedAt
    ) {
        public static AdminCouponResponse from(CouponInfo info) {
            return new AdminCouponResponse(
                info.couponId(),
                info.name(),
                info.type(),
                info.value(),
                info.minOrderAmount(),
                info.expiredAt(),
                info.createdAt(),
                info.updatedAt()
            );
        }
    }

    public record AdminUserCouponResponse(
        Long userCouponId,
        Long userId,
        Long couponId,
        String couponName,
        String status,
        ZonedDateTime issuedAt,
        ZonedDateTime usedAt
    ) {
        public static AdminUserCouponResponse from(UserCouponInfo info) {
            return new AdminUserCouponResponse(
                info.userCouponId(),
                info.userId(),
                info.couponId(),
                info.couponName(),
                info.status(),
                info.issuedAt(),
                info.usedAt()
            );
        }
    }

    public record PageResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages
    ) {
        public static <T> PageResponse<T> from(Page<T> page) {
            return new PageResponse<>(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages()
            );
        }
    }
}
