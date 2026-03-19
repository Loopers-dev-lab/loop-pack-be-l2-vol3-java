package com.loopers.application.order;

import com.loopers.application.coupon.IssuedCouponService;
import com.loopers.application.coupon.IssuedCouponSnapshot;
import com.loopers.application.product.ProductService;
import com.loopers.application.stock.StockService;
import com.loopers.domain.order.Order;
import com.loopers.domain.product.Product;
import com.loopers.domain.order.OrderStatus;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class OrderFacade {

    private final OrderService orderService;
    private final ProductService productService;
    private final StockService stockService;
    private final IssuedCouponService issuedCouponService;

    // Command

    @Transactional
    public OrderInfo placeOrder(Long userId, OrderCommand.Place command) {

        // -- 1단계: 검증 + 계산 (읽기/순수 연산, 상태 변경 없음) --
        Map<Long, Integer> productQuantities = command.toQuantityMap();
        List<Product> products = productService.getActiveProducts(productQuantities.keySet());

        List<OrderCommand.CreateItem> orderItems = command.toCreateItems(products);
        BigDecimal totalAmount = OrderCommand.CreateItem.calculateTotalAmount(orderItems);

        IssuedCouponSnapshot couponSnapshot = command.issuedCouponId() != null
                ? issuedCouponService.createDiscountSnapshot(command.issuedCouponId(), userId, totalAmount)
                : IssuedCouponSnapshot.none();

        // -- 2단계: 상태 변경 (원자적) --
        stockService.reserve(productQuantities);

        if (couponSnapshot.isApplied()) {
            issuedCouponService.markUsedIfAvailable(command.issuedCouponId(), userId);
        }

        OrderCommand.CouponSnapshot orderCoupon = OrderCommand.CouponSnapshot.of(
                couponSnapshot.issuedCouponId(), couponSnapshot.discountAmount());

        Order order = orderService.createOrder(OrderCommand.Create.of(userId, orderItems, orderCoupon));

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
    public Page<OrderInfo.OrderSummary> getOrderList(Long userId, OrderStatus status, ZonedDateTime startDateTime, ZonedDateTime endDateTime, Pageable pageable) {
        Page<Order> orders = orderService.findOrdersByUserIdAndStatusAndDateRange(userId, status, startDateTime, endDateTime, pageable);
        return orders.map(OrderInfo.OrderSummary::from);
    }

    @Transactional(readOnly = true)
    public OrderInfo getAdminOrderDetail(Long orderId) {
        Order order = orderService.getOrder(orderId);
        return OrderInfo.from(order);
    }

    @Transactional(readOnly = true)
    public Page<OrderInfo.OrderAdminSummary> getAdminOrderList(OrderStatus status, Pageable pageable) {
        Page<Order> orders = (status != null)
                ? orderService.findOrdersByStatus(status, pageable)
                : orderService.findAllOrders(pageable);
        return orders.map(OrderInfo.OrderAdminSummary::from);
    }

    @Transactional(readOnly = true)
    public Page<OrderInfo.OrderAdminSummary> getAdminOrdersByProduct(Long productId, Pageable pageable) {
        Page<Order> orders = orderService.findOrdersByProductId(productId, pageable);
        return orders.map(OrderInfo.OrderAdminSummary::from);
    }
}
