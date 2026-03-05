package com.loopers.application.order;

import com.loopers.domain.coupon.CouponService;
import com.loopers.domain.order.Order;
import com.loopers.domain.order.OrderHistoryService;
import com.loopers.domain.order.OrderItem;
import com.loopers.domain.order.OrderService;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductService;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class OrderFacade {

    private final OrderService orderService;
    private final OrderHistoryService orderHistoryService;
    private final CouponService couponService;
    private final ProductService productService;

    public record OrderItemRequest(Long productId, Integer quantity) {}

    @Transactional
    public OrderInfo createOrder(Long userId, List<OrderItemRequest> itemRequests, Long userCouponId) {
        List<OrderItem> orderItems = itemRequests.stream()
                .sorted(Comparator.comparing(OrderItemRequest::productId))
                .map(r -> {
                    Product product = productService.decreaseStock(r.productId(), r.quantity());
                    return OrderItem.create(
                            product.getId(),
                            product.getName(),
                            product.getPrice(),
                            r.quantity()
                    );
                })
                .toList();

        BigDecimal discountAmount = BigDecimal.ZERO;
        if (userCouponId != null) {
            BigDecimal originalAmount = orderItems.stream()
                    .map(OrderItem::getTotalPrice)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            discountAmount = couponService.useUserCoupon(userCouponId, userId, originalAmount);
        }

        Order order = orderService.createOrder(userId, orderItems, discountAmount, userCouponId);
        return OrderInfo.from(order);
    }

    public OrderInfo getOrder(Long orderId, Long userId) {
        Order order = orderService.getById(orderId);
        order.validateOwner(userId);
        return OrderInfo.from(order);
    }

    public OrderInfo getOrderForAdmin(Long orderId) {
        Order order = orderService.getById(orderId);
        return OrderInfo.from(order);
    }

    public Page<OrderInfo> getOrdersByUserId(Long userId, Pageable pageable) {
        return orderService.getOrdersByUserId(userId, pageable)
                .map(OrderInfo::from);
    }

    public Page<OrderInfo> getAllOrders(Pageable pageable) {
        return orderService.getAllOrders(pageable)
                .map(OrderInfo::from);
    }

    public List<OrderHistoryInfo> getOrderHistories(Long orderId, Long userId) {
        Order order = orderService.getById(orderId);
        order.validateOwner(userId);
        return orderHistoryService.getHistoriesByOrderId(orderId).stream()
                .map(OrderHistoryInfo::from)
                .toList();
    }

    public List<OrderHistoryInfo> getOrderHistoriesForAdmin(Long orderId) {
        orderService.getById(orderId);
        return orderHistoryService.getHistoriesByOrderId(orderId).stream()
                .map(OrderHistoryInfo::from)
                .toList();
    }
}
