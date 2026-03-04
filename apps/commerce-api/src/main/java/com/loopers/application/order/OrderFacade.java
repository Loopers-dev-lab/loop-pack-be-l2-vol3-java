package com.loopers.application.order;

import com.loopers.application.coupon.CouponService;
import com.loopers.application.coupon.IssuedCouponService;
import com.loopers.application.product.ProductService;
import com.loopers.domain.coupon.Coupon;
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
    private final CouponService couponService;

    // Command

    @Transactional
    public OrderInfo placeOrder(Long userId, OrderCommand.Place command) {
        var items = command.items();

        Set<Long> productIds = items.stream()
                .map(OrderCommand.PlaceItem::productId)
                .collect(Collectors.toSet());

        if (productIds.size() != items.size()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "주문 상품이 중복되었습니다");
        }

        // 쿠폰 검증 (락 획득)
        IssuedCoupon issuedCoupon = null;
        Coupon coupon = null;
        if (command.couponId() != null) {
            issuedCoupon = issuedCouponService.getIssuedCouponForUpdate(command.couponId());
            if (!issuedCoupon.getUserId().equals(userId)) {
                throw new CoreException(ErrorType.NOT_FOUND, "존재하지 않는 쿠폰입니다");
            }
            if (issuedCoupon.isDeleted()) {
                throw new CoreException(ErrorType.NOT_FOUND, "존재하지 않는 쿠폰입니다");
            }
            coupon = couponService.getActiveCoupon(issuedCoupon.getCouponId());
            if (issuedCoupon.isUsed() || coupon.isExpired()) {
                throw new CoreException(ErrorType.BAD_REQUEST, "사용할 수 없는 쿠폰입니다");
            }
        }

        // 재고 차감
        Map<Long, Integer> productQuantities = items.stream()
                .collect(Collectors.toMap(
                        OrderCommand.PlaceItem::productId,
                        OrderCommand.PlaceItem::quantity
                ));
        List<Product> products = productService.deductStocks(productQuantities);

        Map<Long, Product> productMap = products.stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));

        List<OrderCommand.CreateItem> orderItems = items.stream()
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

        // 주문 생성
        Order order = orderService.createOrder(OrderCommand.Create.of(userId, orderItems));

        // 쿠폰 적용
        if (issuedCoupon != null) {
            if (coupon.getMinOrderAmount() != null
                    && order.getTotalAmount().compareTo(coupon.getMinOrderAmount()) < 0) {
                throw new CoreException(ErrorType.BAD_REQUEST, "최소 주문 금액 조건을 충족하지 않습니다");
            }
            BigDecimal discountAmount = coupon.calculateDiscount(order.getTotalAmount());
            order.applyCoupon(issuedCoupon.getId(), discountAmount);
            issuedCoupon.use();
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
}
