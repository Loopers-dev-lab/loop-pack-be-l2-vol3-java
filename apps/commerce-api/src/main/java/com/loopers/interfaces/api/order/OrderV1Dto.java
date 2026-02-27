package com.loopers.interfaces.api.order;

import com.loopers.application.order.OrderService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public class OrderV1Dto {

    public record OrderCreateRequest(
        @NotNull(message = "회원 ID는 필수입니다")
        Long memberId,

        @Valid
        @NotNull(message = "주문 항목은 필수입니다")
        List<OrderLineRequest> items
    ) {}

    public record OrderLineRequest(
        @NotNull(message = "상품 ID는 필수입니다")
        Long productId,

        @NotNull(message = "수량은 필수입니다")
        @Min(value = 1, message = "수량은 1 이상이어야 합니다")
        Integer quantity
    ) {}

    public record OrderCreateResponse(
        Long orderId,
        String status,
        long totalAmount,
        List<OrderLineResponse> orderLines
    ) {
        public static OrderCreateResponse from(OrderService.OrderResult result) {
            List<OrderLineResponse> lines = result.orderLines().stream()
                .map(ol -> new OrderLineResponse(ol.productId(), ol.quantity(), ol.unitPrice()))
                .toList();
            return new OrderCreateResponse(
                result.orderId(),
                result.status(),
                result.totalAmount(),
                lines
            );
        }
    }

    public record OrderLineResponse(Long productId, int quantity, long unitPrice) {}
}
