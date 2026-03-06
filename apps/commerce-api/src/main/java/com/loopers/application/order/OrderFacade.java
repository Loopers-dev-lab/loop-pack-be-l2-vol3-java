package com.loopers.application.order;

import com.loopers.domain.coupon.CouponDiscount;
import com.loopers.domain.coupon.CouponService;
import com.loopers.domain.order.OrderModel;
import com.loopers.domain.order.OrderService;
import com.loopers.domain.product.ProductService;
import com.loopers.domain.product.ProductSnapshot;
import com.loopers.domain.product.ProductValidationRequest;
import com.loopers.domain.product.Quantity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 주문 유스케이스 조율.
 * 트랜잭션 경계, 도메인 결과 → OrderInfo 변환.
 * 쿠폰 적용 시 CouponService.validateAndUse 후 OrderService.create 호출.
 */
@Service
public class OrderFacade {

    private final OrderService orderService;
    private final ProductService productService;
    private final CouponService couponService;

    public OrderFacade(OrderService orderService, ProductService productService, CouponService couponService) {
        this.orderService = orderService;
        this.productService = productService;
        this.couponService = couponService;
    }

    @Transactional
    public OrderInfo placeOrder(Long userId, List<CreateOrderItemParam> params, Long couponId) {
        List<ProductValidationRequest> requests = params.stream()
                .map(p -> new ProductValidationRequest(p.productId(), Quantity.of(p.quantity()), p.optionId()))
                .toList();
        List<ProductSnapshot> snapshots = productService.validateAndGetSnapshots(requests);
        BigDecimal amountBeforeDiscount = computeAmountBeforeDiscount(snapshots, requests);
        Optional<CouponDiscount> couponDiscount;
        Long issuedCouponId;
        if (couponId != null) {
            CouponDiscount discount = couponService.validateAndUse(couponId, userId, amountBeforeDiscount);
            couponDiscount = Optional.of(discount);
            issuedCouponId = couponId;
        } else {
            couponDiscount = Optional.empty();
            issuedCouponId = null;
        }
        productService.decreaseStockWithLock(requests);
        OrderModel order = orderService.create(userId, requests, snapshots, couponDiscount, issuedCouponId);
        return OrderInfo.from(order);
    }

    /** 쿠폰 미적용 주문. */
    @Transactional
    public OrderInfo placeOrder(Long userId, List<CreateOrderItemParam> params) {
        return placeOrder(userId, params, null);
    }

    @Transactional(readOnly = true)
    public Optional<OrderInfo> findById(Long userId, Long orderId) {
        return orderService.findById(userId, orderId).map(OrderInfo::from);
    }

    @Transactional(readOnly = true)
    public List<OrderInfo> findOrders(Long userId, ZonedDateTime start, ZonedDateTime end, int page, int size) {
        return orderService.findOrders(userId, start, end, page, size).stream()
                .map(OrderInfo::from)
                .toList();
    }

    @Transactional
    public OrderInfo cancel(Long userId, Long orderId) {
        OrderModel order = orderService.cancel(userId, orderId);
        return OrderInfo.from(order);
    }

    private static BigDecimal computeAmountBeforeDiscount(List<ProductSnapshot> snapshots,
                                                          List<ProductValidationRequest> requests) {
        BigDecimal sum = BigDecimal.ZERO;
        for (int i = 0; i < snapshots.size(); i++) {
            BigDecimal lineTotal = snapshots.get(i).price().value()
                    .multiply(BigDecimal.valueOf(requests.get(i).quantity().value()));
            sum = sum.add(lineTotal);
        }
        return sum;
    }
}
