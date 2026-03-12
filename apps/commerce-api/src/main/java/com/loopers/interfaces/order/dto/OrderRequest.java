package com.loopers.interfaces.order.dto;

import com.loopers.application.order.dto.OrderCriteria;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.time.ZonedDateTime;
import java.util.List;

public class OrderRequest {

    public record ListRequest(
        ZonedDateTime startAt,
        ZonedDateTime endAt
    ) {
        public OrderCriteria.ListByDate toCriteria() {
            return new OrderCriteria.ListByDate(startAt, endAt);
        }
    }

    public record Create(
        @NotEmpty(message = "주문 항목은 필수 입력값입니다.")
        @Valid
        List<OrderItemRequest> items,
        Long couponId
    ) {
        public OrderCriteria.Create toCriteria() {
            return new OrderCriteria.Create(
                    items.stream()
                            .map(item -> new OrderCriteria.Create.CreateItem(
                                    item.productId(), item.quantity(), item.expectedPrice()))
                            .toList(),
                    couponId);
        }
    }

    public record OrderItemRequest(
        @NotNull(message = "상품 ID는 필수 입력값입니다.")
        Long productId,
        @NotNull(message = "수량은 필수 입력값입니다.")
        @Min(value = 1, message = "수량은 1 이상이어야 합니다.")
        Integer quantity,
        @NotNull(message = "예상 가격은 필수 입력값입니다.")
        @Min(value = 0, message = "예상 가격은 0 이상이어야 합니다.")
        Integer expectedPrice
    ) {}
}
