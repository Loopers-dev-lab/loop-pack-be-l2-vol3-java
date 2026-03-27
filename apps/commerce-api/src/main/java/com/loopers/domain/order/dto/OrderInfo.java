package com.loopers.domain.order.dto;

import com.loopers.domain.order.OrderItemModel;
import com.loopers.domain.order.OrderModel;
import java.util.List;

public class OrderInfo {

    public record CancelledItem(Long productId, int quantity, boolean orderFullyCancelled) {

        public static CancelledItem from(OrderItemModel item, OrderModel order) {
            return new CancelledItem(
                    item.getProductId(), item.getQuantity(), order.isCancelled());
        }
    }

    public record PaymentFailureCancellation(
            Long userId,
            int totalPrice,
            List<CancelledItem> items) {

        public record CancelledItem(Long productId, int quantity) {}
    }
}
