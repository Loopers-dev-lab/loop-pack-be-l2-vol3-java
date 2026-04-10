package com.loopers.application.order;

import com.loopers.application.order.dto.OrderCriteria;
import com.loopers.application.order.dto.OrderResult;
import com.loopers.domain.brand.BrandService;
import com.loopers.domain.coupon.CouponService;
import com.loopers.domain.order.OrderModel;
import com.loopers.domain.order.OrderService;
import com.loopers.domain.order.dto.OrderInfo;
import com.loopers.domain.order.dto.OrderCommand;
import com.loopers.domain.product.ProductService;
import com.loopers.domain.product.dto.ProductInfo;
import com.loopers.domain.user.UserService;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OrderFacade {

    private final ProductService productService;
    private final BrandService brandService;
    private final OrderService orderService;
    private final CouponService couponService;
    private final UserService userService;

    @Retryable(
            retryFor = ObjectOptimisticLockingFailureException.class,
            maxAttempts = 10,
            backoff = @Backoff(delay = 50, random = true))
    @Transactional
    public OrderResult.OrderSummary createOrder(Long userId, OrderCriteria.Create criteria) {
        List<ProductInfo.StockDeduction> deductionInfos =
                productService.validateAndDeductStock(criteria.toStockDeductions());

        Map<Long, String> brandNameMap = brandService.getNameMapByIds(
                ProductInfo.StockDeduction.extractDistinctBrandIds(deductionInfos));

        OrderModel order = orderService.createOrder(
                userId, OrderCommand.CreateItem.from(deductionInfos, brandNameMap));

        if (criteria.ownedCouponId() != null) {
            orderService.applyDiscount(
                    order,
                    (int) couponService.useAndCalculateDiscount(
                            criteria.ownedCouponId(), userId, order.getId(),
                            order.getOriginalTotalPrice()));
        }

        userService.deductPoint(userId, order.getTotalPrice());

        return OrderResult.OrderSummary.from(order);
    }

    @Transactional(readOnly = true)
    public List<OrderResult.OrderSummary> getMyOrders(Long userId, OrderCriteria.ListByDate criteria) {
        return orderService.getOrdersByUserIdAndPeriod(userId, criteria.startAt(), criteria.endAt()).stream()
                .map(OrderResult.OrderSummary::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public OrderResult.OrderDetail getMyOrderDetail(Long userId, Long orderId) {
        return OrderResult.OrderDetail.from(
                orderService.getByIdAndUserId(orderId, userId));
    }

    @Transactional(readOnly = true)
    public Page<OrderResult.OrderSummary> getAllOrders(Pageable pageable) {
        return orderService.getAllOrders(pageable)
                .map(OrderResult.OrderSummary::from);
    }

    @Transactional(readOnly = true)
    public OrderResult.OrderDetail getOrderDetail(Long orderId) {
        return OrderResult.OrderDetail.from(orderService.getById(orderId));
    }

    @Retryable(
            retryFor = ObjectOptimisticLockingFailureException.class,
            maxAttempts = 5,
            backoff = @Backoff(delay = 50, random = true))
    @Transactional
    public void cancelMyOrderItem(Long userId, Long orderId, Long orderItemId) {
        OrderModel order = orderService.getByIdAndUserId(orderId, userId);
        OrderInfo.CancelledItem cancelledItem = orderService.cancelItem(order, orderItemId);
        productService.increaseStock(cancelledItem.productId(), cancelledItem.quantity());
        if (cancelledItem.orderFullyCancelled()) {
            couponService.restoreByOrderId(orderId);
        }
    }

    @Retryable(
            retryFor = ObjectOptimisticLockingFailureException.class,
            maxAttempts = 5,
            backoff = @Backoff(delay = 50, random = true))
    @Transactional
    public void cancelOrderItem(Long orderId, Long orderItemId) {
        OrderModel order = orderService.getById(orderId);
        OrderInfo.CancelledItem cancelledItem = orderService.cancelItem(order, orderItemId);
        productService.increaseStock(cancelledItem.productId(), cancelledItem.quantity());
        if (cancelledItem.orderFullyCancelled()) {
            couponService.restoreByOrderId(orderId);
        }
    }
}
