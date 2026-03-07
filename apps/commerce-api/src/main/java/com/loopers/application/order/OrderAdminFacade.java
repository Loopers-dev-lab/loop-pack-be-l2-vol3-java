package com.loopers.application.order;

import com.loopers.domain.order.Order;
import com.loopers.domain.order.OrderItem;
import com.loopers.domain.order.OrderService;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;
import java.util.List;

/**
 * 어드민 주문 Facade
 *
 * 어드민 주문 목록 조회, 상세 조회 유스케이스를 처리한다.
 */
@Component
public class OrderAdminFacade {

    private final OrderService orderService;

    public OrderAdminFacade(OrderService orderService) {
        this.orderService = orderService;
    }

    /** 전체 주문 목록 페이지네이션 조회 */
    @Transactional(readOnly = true)
    public OrderAdminListResult getOrders(int page, int size) {
        List<Order> orders = orderService.getAllOrders(page, size);
        long totalElements = orderService.countAllOrders();
        int totalPages = size > 0 ? (int) Math.ceil((double) totalElements / size) : 0;

        List<OrderSummary> summaries = orders.stream()
                .map(o -> new OrderSummary(
                        o.getId(), o.getOrderNumber(), o.getUserId(),
                        o.getStatus().name(), o.getTotalAmount(), o.getCreatedAt()))
                .toList();

        return new OrderAdminListResult(summaries, page, size, totalElements, totalPages);
    }

    /** 주문 상세 조회 (소유권 검증 없음 — 어드민) */
    @Transactional(readOnly = true)
    public OrderAdminDetailResult getOrderDetail(Long orderId) {
        Order order = orderService.getById(orderId);

        List<OrderItemDetail> items = order.getItems().stream()
                .map(item -> new OrderItemDetail(
                        item.getProductName(), item.getBrandName(),
                        item.getUnitPrice(), item.getQuantity(), item.getLineTotal()))
                .toList();

        return new OrderAdminDetailResult(
                order.getId(), order.getOrderNumber(), order.getUserId(),
                order.getStatus().name(),
                order.getOrdererName(), order.getOrdererPhone(),
                order.getReceiverName(), order.getReceiverPhone(),
                order.getZipCode(), order.getAddressLine1(), order.getAddressLine2(),
                order.getSubtotalAmount(), order.getDiscountAmount(),
                order.getPointUsedAmount(), order.getShippingFee(), order.getTotalAmount(),
                items, order.getCreatedAt());
    }

    public record OrderAdminListResult(
            List<OrderSummary> orders,
            int page, int size, long totalElements, int totalPages) {}

    public record OrderSummary(
            Long orderId, String orderNumber, Long userId,
            String status, int totalAmount, ZonedDateTime createdAt) {}

    public record OrderAdminDetailResult(
            Long orderId, String orderNumber, Long userId, String status,
            String ordererName, String ordererPhone,
            String receiverName, String receiverPhone,
            String zipCode, String addressLine1, String addressLine2,
            int subtotalAmount, int discountAmount,
            int pointUsedAmount, int shippingFee, int totalAmount,
            List<OrderItemDetail> items, ZonedDateTime createdAt) {}

    public record OrderItemDetail(
            String productName, String brandName,
            int unitPrice, int quantity, int lineTotal) {}
}
