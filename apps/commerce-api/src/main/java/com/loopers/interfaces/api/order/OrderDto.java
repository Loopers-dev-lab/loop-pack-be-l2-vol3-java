package com.loopers.interfaces.api.order;

import com.loopers.application.order.OrderDetail;
import com.loopers.application.order.OrderSummary;

import java.time.ZonedDateTime;
import java.util.List;

public class OrderDto {

    public static record CreateOrderRequest(List<OrderItem> items, Long couponId) {
        public static record OrderItem(Long productId, Integer quantity) {

        }
    }

    public static record CreateOrderResponse(String orderId) {

    }

    public static record SummaryResponse(String orderId, ZonedDateTime orderedAt, Long totalPrice, Integer itemCount) {

        public static SummaryResponse from(OrderSummary orderSummary) {
            return new SummaryResponse(orderSummary.orderId(), orderSummary.orderedAt(), orderSummary.totalPrice(), orderSummary.itemCount());
        }
    }

    public static record DetailResponse(Long orderId, Long totalPrice, List<OrderDto.ItemResponse> items) {

        public static DetailResponse from(OrderDetail orderDetail) {
            List<ItemResponse> itemResponses = orderDetail.items().stream()
                    .map(item -> new ItemResponse(item.productName(), item.brandName(), item.unitPrice(), item.quantity()))
                    .toList();
            return new DetailResponse(orderDetail.orderId(), orderDetail.totalPrice(), itemResponses);
        }
    }

    public static record ItemResponse(String productName, String brandName, Integer unitPrice, Integer quantity) {

    }
}
