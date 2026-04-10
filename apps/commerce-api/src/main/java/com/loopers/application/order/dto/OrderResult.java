package com.loopers.application.order.dto;

import com.loopers.domain.order.OrderItemModel;
import com.loopers.domain.order.OrderModel;
import com.loopers.domain.payment.PaymentModel;
import java.time.ZonedDateTime;
import java.util.List;

public class OrderResult {

    public record OrderPaymentSummary(
        Long orderId,
        int totalPrice,
        String orderStatus,
        Long paymentId,
        String paymentStatus,
        String cardType,
        String maskedCardNo,
        ZonedDateTime createdAt
    ) {
        public static OrderPaymentSummary from(OrderModel order, PaymentModel payment) {
            return new OrderPaymentSummary(
                    order.getId(),
                    order.getTotalPrice(),
                    order.getStatus().name(),
                    payment.getId(),
                    payment.getStatus().name(),
                    payment.getCardType().name(),
                    payment.getMaskedCardNo(),
                    order.getCreatedAt());
        }

        public static OrderPaymentSummary pending(OrderModel order) {
            return new OrderPaymentSummary(
                    order.getId(),
                    order.getTotalPrice(),
                    order.getStatus().name(),
                    null, null, null, null,
                    order.getCreatedAt());
        }
    }

    public record OrderSummary(
        Long orderId,
        int totalPrice,
        int originalTotalPrice,
        int discountAmount,
        String status,
        ZonedDateTime createdAt
    ) {
        public static OrderSummary from(OrderModel model) {
            return new OrderSummary(
                    model.getId(),
                    model.getTotalPrice(),
                    model.getOriginalTotalPrice(),
                    model.getDiscountAmount(),
                    model.getStatus().name(),
                    model.getCreatedAt());
        }
    }

    public record OrderDetail(
        Long orderId,
        Long userId,
        int totalPrice,
        String status,
        ZonedDateTime createdAt,
        List<OrderItemDetail> items
    ) {
        public static OrderDetail from(OrderModel model) {
            return new OrderDetail(
                    model.getId(),
                    model.getUserId(),
                    model.getTotalPrice(),
                    model.getStatus().name(),
                    model.getCreatedAt(),
                    model.getItems().stream().map(OrderItemDetail::from).toList());
        }
    }

    public record OrderItemDetail(
        Long orderItemId,
        Long productId,
        String productName,
        String brandName,
        int orderPrice,
        int quantity
    ) {
        public static OrderItemDetail from(OrderItemModel model) {
            return new OrderItemDetail(
                    model.getId(),
                    model.getProductId(),
                    model.getProductName(),
                    model.getBrandName(),
                    model.getOrderPrice(),
                    model.getQuantity());
        }
    }
}
