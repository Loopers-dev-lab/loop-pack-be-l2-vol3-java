package com.loopers.interfaces.api.coupon;

import com.loopers.application.coupon.UserCouponInfo;
import com.loopers.application.coupon.CouponTemplateInfo;
import com.loopers.domain.coupon.CouponStatus;
import com.loopers.domain.coupon.CouponType;

import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.util.List;

public class CouponAdminV1Dto {

    /**
     * 쿠폰 템플릿 등록 요청
     */
    public record CouponTemplateRegisterRequest(
            String name,
            CouponType type,
            int value,
            Integer minOrderAmount,
            LocalDateTime expiredAt
    ) {}

    /**
     * 쿠폰 템플릿 수정 요청
     */
    public record CouponTemplateUpdateRequest(
            String name,
            CouponType type,
            int value,
            Integer minOrderAmount,
            LocalDateTime expiredAt
    ) {}

    /**
     * 쿠폰 템플릿 응답 (단건/목록 공용)
     */
    public record CouponTemplateResponse(
            Long id,
            String name,
            CouponType type,
            int value,
            Integer minOrderAmount,
            LocalDateTime expiredAt,
            ZonedDateTime createdAt
    ) {
        public static CouponTemplateResponse from(CouponTemplateInfo info) {
            return new CouponTemplateResponse(
                    info.id(),
                    info.name(),
                    info.type(),
                    info.value(),
                    info.minOrderAmount(),
                    info.expiredAt(),
                    info.createdAt()
            );
        }
    }

    /**
     * 쿠폰 템플릿 목록 응답 (페이징)
     */
    public record CouponTemplateListResponse(
            List<CouponTemplateResponse> templates,
            long totalElements,
            int totalPages
    ) {
        public static CouponTemplateListResponse from(org.springframework.data.domain.Page<CouponTemplateInfo> page) {
            return new CouponTemplateListResponse(
                    page.getContent().stream().map(CouponTemplateResponse::from).toList(),
                    page.getTotalElements(),
                    page.getTotalPages()
            );
        }
    }

    /**
     * 발급 쿠폰 단건 응답
     */
    public record UserCouponResponse(
            Long userCouponId,
            Long couponTemplateId,
            Long userId,
            CouponStatus status,
            LocalDateTime expiredAt,
            ZonedDateTime issuedAt,
            LocalDateTime usedAt
    ) {
        public static UserCouponResponse from(UserCouponInfo info) {
            return new UserCouponResponse(
                    info.id(),
                    info.couponTemplateId(),
                    info.userId(),
                    info.status(),
                    info.expiredAt(),
                    info.issuedAt(),
                    info.usedAt()
            );
        }
    }

    /**
     * 발급 쿠폰 목록 응답 (페이징)
     */
    public record UserCouponListResponse(
            List<UserCouponResponse> userCoupons,
            long totalElements,
            int totalPages
    ) {
        public static UserCouponListResponse from(org.springframework.data.domain.Page<UserCouponInfo> page) {
            return new UserCouponListResponse(
                    page.getContent().stream().map(UserCouponResponse::from).toList(),
                    page.getTotalElements(),
                    page.getTotalPages()
            );
        }
    }
}
