package com.loopers.application.order;

import com.loopers.domain.order.OrderItemModel;
import com.loopers.domain.order.OrderModel;
import com.loopers.domain.order.OrderService;
import com.loopers.domain.coupon.UserCouponModel;
import com.loopers.domain.coupon.UserCouponService;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductService;
import com.loopers.domain.user.UserModel;
import com.loopers.domain.user.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@RequiredArgsConstructor
@Component
public class OrderFacade {

    private final OrderService orderService;
    private final ProductService productService;
    private final UserService userService;
    private final UserCouponService userCouponService;

    @Transactional
    public OrderDetailInfo placeOrder(String loginId, String password, List<PlaceOrderItem> items, Long couponId) {
        UserModel user = userService.getMyInfo(loginId, password);

        List<OrderItemModel> orderItems = items.stream()
            .map(item -> {
                ProductModel product = productService.getProduct(item.productId());
                productService.deductStock(item.productId(), item.quantity());
                return new OrderItemModel(
                    product.getId(),
                    product.getName(),
                    product.getPrice(),
                    item.quantity()
                );
            })
            .toList();

        long originalAmount = orderItems.stream()
            .mapToLong(OrderItemModel::getLineTotalAmount)
            .sum();

        Long discountAmount = 0L;
        UserCouponModel userCoupon = null;
        if (couponId != null) {
            userCoupon = userCouponService.getAvailableUserCouponForUse(user.getId(), couponId);
            discountAmount = userCouponService.calculateDiscountAmount(userCoupon, originalAmount);
        }

        OrderModel order = orderService.placeOrder(
            user.getId(),
            orderItems,
            discountAmount,
            userCoupon != null ? userCoupon.getId() : null
        );

        if (userCoupon != null) {
            userCouponService.markUsed(userCoupon, order.getId(), originalAmount);
        }

        return OrderDetailInfo.from(order);
    }

    public List<OrderSummaryInfo> getMyOrders(String loginId, String password, LocalDate startAt, LocalDate endAt) {
        UserModel user = userService.getMyInfo(loginId, password);
        return orderService.getMyOrders(user.getId(), startAt, endAt)
            .stream()
            .map(OrderSummaryInfo::from)
            .toList();
    }

    public OrderDetailInfo getMyOrder(String loginId, String password, Long orderId) {
        UserModel user = userService.getMyInfo(loginId, password);
        OrderModel order = orderService.getMyOrder(user.getId(), orderId);
        return OrderDetailInfo.from(order);
    }

    public Page<OrderSummaryInfo> getAll(Pageable pageable) {
        return orderService.getAll(pageable).map(OrderSummaryInfo::from);
    }

    public OrderDetailInfo getOrder(Long orderId) {
        OrderModel order = orderService.getOrder(orderId);
        return OrderDetailInfo.from(order);
    }

    public record PlaceOrderItem(Long productId, int quantity) {}
}
