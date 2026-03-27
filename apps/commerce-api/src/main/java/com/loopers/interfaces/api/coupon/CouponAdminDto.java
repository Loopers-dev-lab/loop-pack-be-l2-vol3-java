package com.loopers.interfaces.api.coupon;

import com.loopers.application.coupon.command.CreateCouponCommand;
import com.loopers.application.coupon.command.UpdateCouponCommand;
import com.loopers.application.coupon.view.CouponIssueView;
import com.loopers.domain.coupon.Coupon;
import com.loopers.domain.coupon.CouponStatus;
import com.loopers.domain.coupon.CouponType;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.data.domain.Page;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public class CouponAdminDto {

    public record CreateCouponRequest(
            @NotBlank(message = "쿠폰 이름은 필수입니다")
            String name,
            @NotNull(message = "쿠폰 타입은 필수입니다")
            CouponType type,
            @Min(value = 1, message = "쿠폰 값은 1 이상이어야 합니다")
            int value,
            @Min(value = 0, message = "최소 주문 금액은 0 이상이어야 합니다")
            int minOrderAmount,
            @Min(value = 1, message = "쿠폰 수량은 1 이상이어야 합니다")
            int totalQuantity,
            @NotNull(message = "만료 일시는 필수입니다")
            LocalDateTime expiredAt
    ) {
        public CreateCouponCommand toCommand() {
            return new CreateCouponCommand(name, type, value, minOrderAmount, totalQuantity, expiredAt);
        }
    }

    public record UpdateCouponRequest(
            @NotBlank(message = "쿠폰 이름은 필수입니다")
            String name,
            @NotNull(message = "쿠폰 타입은 필수입니다")
            CouponType type,
            @Min(value = 1, message = "쿠폰 값은 1 이상이어야 합니다")
            int value,
            @Min(value = 0, message = "최소 주문 금액은 0 이상이어야 합니다")
            int minOrderAmount,
            @Min(value = 1, message = "쿠폰 수량은 1 이상이어야 합니다")
            int totalQuantity,
            @NotNull(message = "만료 일시는 필수입니다")
            LocalDateTime expiredAt
    ) {
        public UpdateCouponCommand toCommand() {
            return new UpdateCouponCommand(name, type, value, minOrderAmount, totalQuantity, expiredAt);
        }
    }

    public record CouponResponse(
            UUID id,
            String name,
            CouponType type,
            int value,
            int minOrderAmount,
            int totalQuantity,
            int remainingQuantity,
            LocalDateTime expiredAt
    ) {
        public static CouponResponse from(Coupon coupon) {
            return new CouponResponse(
                    coupon.id(),
                    coupon.name(),
                    coupon.type(),
                    coupon.value(),
                    coupon.minOrderAmount(),
                    coupon.totalQuantity(),
                    coupon.remainingQuantity(),
                    coupon.expiredAt()
            );
        }
    }

    public record CouponListResponse(
            List<CouponResponse> items,
            int page,
            int size,
            long totalElements,
            int totalPages
    ) {
        public static CouponListResponse from(Page<Coupon> pageData) {
            return new CouponListResponse(
                    pageData.getContent().stream().map(CouponResponse::from).toList(),
                    pageData.getNumber(),
                    pageData.getSize(),
                    pageData.getTotalElements(),
                    pageData.getTotalPages()
            );
        }
    }

    public record CouponIssueResponse(
            UUID couponId,
            String memberId,
            CouponStatus status,
            LocalDateTime issuedAt,
            LocalDateTime expiredAt,
            LocalDateTime usedAt
    ) {
        public static CouponIssueResponse from(CouponIssueView view) {
            return new CouponIssueResponse(
                    view.couponId(),
                    view.memberId(),
                    view.status(),
                    view.issuedAt(),
                    view.expiredAt(),
                    view.usedAt()
            );
        }
    }

    public record CouponIssueListResponse(
            List<CouponIssueResponse> items,
            int page,
            int size,
            long totalElements,
            int totalPages
    ) {
        public static CouponIssueListResponse from(Page<CouponIssueView> pageData) {
            return new CouponIssueListResponse(
                    pageData.getContent().stream().map(CouponIssueResponse::from).toList(),
                    pageData.getNumber(),
                    pageData.getSize(),
                    pageData.getTotalElements(),
                    pageData.getTotalPages()
            );
        }
    }
}
