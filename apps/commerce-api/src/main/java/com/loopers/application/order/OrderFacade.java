package com.loopers.application.order;

import com.loopers.application.coupon.IssuedCouponService;
import com.loopers.application.product.ProductService;
import com.loopers.domain.coupon.IssuedCoupon;
import com.loopers.domain.order.Order;
import com.loopers.domain.product.Product;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class OrderFacade {

    private final OrderService orderService;
    private final ProductService productService;
    private final IssuedCouponService issuedCouponService;

    // Command

    @Transactional
    public OrderInfo placeOrder(Long userId, OrderCommand.Place command) {
        var items = command.items();

        // ── 1단계: 검증 (락 없음, 읽기만) ──

        Set<Long> productIds = items.stream()
                .map(OrderCommand.PlaceItem::productId)
                .collect(Collectors.toSet());
        if (productIds.size() != items.size()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "주문 상품이 중복되었습니다");
        }

        Map<Long, Integer> productQuantities = items.stream()
                .collect(Collectors.toMap(
                        OrderCommand.PlaceItem::productId,
                        OrderCommand.PlaceItem::quantity
                ));

        List<Product> products = new ArrayList<>(productQuantities.keySet()).stream()
                .map(productService::getActiveProduct)
                .toList();

        if (products.size() != productQuantities.size()) {
            throw new CoreException(ErrorType.NOT_FOUND, "존재하지 않는 상품이 포함되어 있습니다");
        }

        IssuedCoupon issuedCoupon = null;
        if (command.couponId() != null) {
            issuedCoupon = issuedCouponService.getIssuedCoupon(command.couponId());
            if (!issuedCoupon.isOwnedBy(userId)) {
                throw new CoreException(ErrorType.NOT_FOUND, "존재하지 않는 쿠폰입니다");
            }
            issuedCoupon.validateUsable();
        }

        // ── 2단계: 계산 (락 없음, 순수 연산) ──

        List<OrderCommand.CreateItem> orderItems = toOrderItems(items, products);
        BigDecimal totalAmount = calculateTotalAmount(orderItems);

        BigDecimal discountAmount = BigDecimal.ZERO;
        if (issuedCoupon != null) {
            issuedCoupon.validateMinOrderAmount(totalAmount);
            discountAmount = issuedCoupon.calculateDiscount(totalAmount);
        }

        // ── 3단계: 상태 변경 (원자적 UPDATE, 최대한 짧게) ──

        productService.decreaseStocks(productQuantities);

        if (command.couponId() != null) {
            issuedCouponService.markUsed(command.couponId(), userId);
        }

        Order order = orderService.createOrder(
                OrderCommand.Create.of(userId, orderItems)
        );

        if (discountAmount.compareTo(BigDecimal.ZERO) > 0) {
            order.applyCoupon(command.couponId(), discountAmount);
        }

        return OrderInfo.from(order);
    }

    // Query

    @Transactional(readOnly = true)
    public OrderInfo getOrderDetail(Long userId, Long orderId) {
        Order order = orderService.getOrder(orderId);
        if (!order.isOwnedBy(userId)) {
            throw new CoreException(ErrorType.NOT_FOUND, "존재하지 않는 주문입니다");
        }
        return OrderInfo.from(order);
    }

    @Transactional(readOnly = true)
    public Page<OrderInfo.OrderSummary> getOrderList(Long userId, ZonedDateTime startDateTime, ZonedDateTime endDateTime, Pageable pageable) {
        Page<Order> orders = orderService.findOrdersByUserIdAndDateRange(userId, startDateTime, endDateTime, pageable);
        return orders.map(OrderInfo.OrderSummary::from);
    }

    @Transactional(readOnly = true)
    public OrderInfo getAdminOrderDetail(Long orderId) {
        Order order = orderService.getOrder(orderId);
        return OrderInfo.from(order);
    }

    @Transactional(readOnly = true)
    public Page<OrderInfo.OrderAdminSummary> getAdminOrderList(Pageable pageable) {
        Page<Order> orders = orderService.findAllOrders(pageable);
        return orders.map(OrderInfo.OrderAdminSummary::from);
    }

    private List<OrderCommand.CreateItem> toOrderItems(
            List<OrderCommand.PlaceItem> items, List<Product> products) {
        Map<Long, Product> productMap = products.stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));

        return items.stream()
                .map(item -> {
                    Product product = productMap.get(item.productId());
                    return OrderCommand.CreateItem.of(
                            product.getId(),
                            product.getName(),
                            product.getPrice(),
                            item.quantity()
                    );
                })
                .toList();
    }

    private BigDecimal calculateTotalAmount(List<OrderCommand.CreateItem> items) {
        return items.stream()
                .map(item -> item.price().multiply(BigDecimal.valueOf(item.quantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
