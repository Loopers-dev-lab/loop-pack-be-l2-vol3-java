package com.loopers.application.order;

import com.loopers.domain.coupon.CouponDiscount;
import com.loopers.domain.coupon.CouponService;
import com.loopers.domain.order.OrderModel;
import com.loopers.domain.order.OrderService;
import com.loopers.domain.product.ProductService;
import com.loopers.domain.product.ProductSnapshot;
import com.loopers.domain.product.ProductValidationRequest;
import com.loopers.domain.product.Quantity;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.OptimisticLockException;
import org.springframework.context.annotation.Lazy;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 주문 유스케이스 조율. 트랜잭션 경계, 도메인 → OrderInfo 변환.
 * 쿠폰 낙관락(@Version) 충돌 시 1회 재시도(백오프 후), 실패 시 409 + "잠시 후 다시 시도해 주세요". (05-transaction-query §3.4, §9.1)
 */
@Service
public class OrderFacade {

    private static final int MAX_ATTEMPTS_WITH_COUPON = 2;
    private static final long RETRY_BACKOFF_MS = 50;

    private final OrderService orderService;
    private final ProductService productService;
    private final CouponService couponService;
    private final OrderFacade self;

    public OrderFacade(OrderService orderService, ProductService productService, CouponService couponService,
                       @Lazy OrderFacade self) {
        this.orderService = orderService;
        this.productService = productService;
        this.couponService = couponService;
        this.self = self;
    }

    /** 주문 접수. 쿠폰 적용 시 검증·사용 → 재고 차감 → 주문 저장(단일 트랜잭션). 낙관락 충돌 시 백오프 후 1회 재시도, 실패 시 409. */
    public OrderInfo placeOrder(Long userId, List<CreateOrderItemParam> params, Long couponId) {
        int maxAttempts = (couponId != null) ? MAX_ATTEMPTS_WITH_COUPON : 1;
        for (int attempt = 0; attempt < maxAttempts; attempt++) {
            try {
                return self.doPlaceOrder(userId, params, couponId);
            } catch (OptimisticLockException | ObjectOptimisticLockingFailureException e) {
                if (couponId == null) {
                    throw new CoreException(ErrorType.INTERNAL_ERROR, "쿠폰 미사용 주문에서 낙관락 예외가 발생했습니다.", e);
                }
                if (attempt == maxAttempts - 1) {
                    throw new CoreException(ErrorType.CONFLICT, "잠시 후 다시 시도해 주세요.");
                }
                backoffBeforeRetry();
            }
        }
        throw new IllegalStateException("unreachable");
    }

    private static void backoffBeforeRetry() {
        try {
            Thread.sleep(RETRY_BACKOFF_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new CoreException(ErrorType.INTERNAL_ERROR, "재시도 대기 중 중단되었습니다.", e);
        }
    }

    /** 단일 트랜잭션: 쿠폰 조회(낙관) → 재고 락(비관) → 주문 생성. 재시도 시 프록시로 새 트랜잭션. */
    @Transactional
    public OrderInfo doPlaceOrder(Long userId, List<CreateOrderItemParam> params, Long couponId) {
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
