package com.loopers.domain.order.model;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

import java.time.LocalDateTime;
import java.util.List;


public final class OrderCommand {

    private OrderCommand() {
    }

    public record Create(Long memberId, List<OrderProduct> orderProducts, int discountAmount, Long userCouponId) {
    }

    public record GetByPeriod(Long memberId, LocalDateTime startAt, LocalDateTime endAt) {
        public GetByPeriod {
            if (startAt != null && endAt != null && startAt.isAfter(endAt)) {
                throw new CoreException(ErrorType.BAD_REQUEST, "시작일은 종료일보다 이전이어야 합니다.");
            }
        }
    }

    public record GetByMember(Long memberId, Long orderId) {
    }

    public record OrderItem(Long productId, int quantity) {
    }

}
