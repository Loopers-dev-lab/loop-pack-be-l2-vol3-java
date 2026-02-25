package com.loopers.application.payment;

import com.loopers.domain.coupon.CouponService;
import com.loopers.domain.coupon.CouponTemplate;
import com.loopers.domain.coupon.IssuedCoupon;
import com.loopers.domain.inventory.InventoryService;
import com.loopers.domain.order.Order;
import com.loopers.domain.order.OrderItem;
import com.loopers.domain.order.OrderService;
import com.loopers.domain.payment.Payment;
import com.loopers.domain.payment.PaymentService;
import com.loopers.domain.point.PointService;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 결제 Facade (F-12, F-13)
 *
 * Order + Payment + Inventory + Point + Coupon 5개 도메인 조율.
 * - F-12: 할인 적용 (쿠폰/포인트)
 * - F-13: 결제 요청 → PG 승인 → 성공 시 다중 도메인 확정 / 실패 시 재고 복구
 */
@Component
public class PaymentFacade {

    private final OrderService orderService;
    private final PaymentService paymentService;
    private final InventoryService inventoryService;
    private final PointService pointService;
    private final CouponService couponService;

    public PaymentFacade(OrderService orderService, PaymentService paymentService,
                         InventoryService inventoryService, PointService pointService,
                         CouponService couponService) {
        this.orderService = orderService;
        this.paymentService = paymentService;
        this.inventoryService = inventoryService;
        this.pointService = pointService;
        this.couponService = couponService;
    }

    /**
     * 할인 적용 (F-12)
     *
     * 1. 주문 조회 + 소유권 검증 + PENDING 확인
     * 2. 쿠폰 할인 계산 (적용 가능 여부 검증)
     * 3. 포인트 잔액 검증 (실제 차감은 결제 성공 시)
     * 4. 주문 금액 재계산
     */
    @Transactional
    public Order applyDiscount(Long orderId, Long userId, Long issuedCouponId, int pointAmount) {
        Order order = orderService.getOrder(orderId, userId);

        int discountAmount = 0;
        if (issuedCouponId != null) {
            IssuedCoupon issuedCoupon = couponService.getIssuedCoupon(issuedCouponId, userId);

            CouponTemplate template = couponService.getTemplate(issuedCoupon.getCouponTemplateId());
            if (!template.isApplicable(order.getSubtotalAmount(), java.time.ZonedDateTime.now())) {
                throw new com.loopers.support.error.CoreException(
                        com.loopers.support.error.CouponErrorType.INVALID_TEMPLATE);
            }
            discountAmount = template.calculateDiscount(order.getSubtotalAmount());
        }

        if (pointAmount > 0) {
            // 잔액 검증만 — 실제 차감은 결제 성공 시
            com.loopers.domain.point.PointAccount account = pointService.getAccount(userId);
            if (account.getBalance() < pointAmount) {
                throw new com.loopers.support.error.CoreException(
                        com.loopers.support.error.PointErrorType.INSUFFICIENT_BALANCE);
            }
        }

        order.applyDiscount(discountAmount, pointAmount, 0);
        return order;
    }

    /**
     * 결제 요청 (F-13)
     *
     * 1. 주문 조회 + 소유권 검증 + PENDING 확인
     * 2. Payment(REQUESTED) 생성 + idempotencyKey
     * 3. PG 결제 승인 요청 (시뮬레이션 — 항상 성공)
     * 4. 성공 시: Payment→APPROVED, 재고 확정, 포인트 차감, 쿠폰 USED, Order→PAID
     * 5. 실패 시: Payment→FAILED, reserved_qty 복구
     */
    @Transactional
    public Payment requestPayment(Long orderId, Long userId, String paymentMethod,
                                   Long issuedCouponId) {
        Order order = orderService.getOrder(orderId, userId);
        order.validateOwnership(userId);

        if (order.getStatus() != com.loopers.domain.order.OrderStatus.PENDING) {
            throw new com.loopers.support.error.CoreException(
                    com.loopers.support.error.OrderErrorType.INVALID_ORDER_STATUS);
        }

        Payment payment = paymentService.create(
                orderId, order.getTotalAmount(), paymentMethod, generateIdempotencyKey());

        // PG 결제 승인 시뮬레이션 (항상 성공)
        boolean pgSuccess = simulatePgApproval(payment);

        if (pgSuccess) {
            String pgTxnId = "PG-TXN-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
            payment.approve(pgTxnId, order.getTotalAmount());

            // 재고 확정 (quantity 차감 + reserved_qty 감소)
            Map<Long, Integer> productQtyMap = order.getItems().stream()
                    .collect(Collectors.toMap(OrderItem::getProductId, OrderItem::getQuantity));
            inventoryService.commitAll(productQtyMap);

            // 포인트 차감 (결제 성공 시에만)
            if (order.getPointUsedAmount() > 0) {
                pointService.use(userId, order.getPointUsedAmount());
            }

            // 쿠폰 USED 처리 (결제 성공 시에만)
            if (issuedCouponId != null) {
                couponService.use(issuedCouponId, userId, orderId);
            }

            // Order → PAID
            orderService.confirm(orderId, payment.getId(), paymentMethod);

            // 포인트 적립
            pointService.earn(userId, order.getTotalAmount());
        } else {
            payment.fail();

            // reserved_qty 복구
            Map<Long, Integer> productQtyMap = order.getItems().stream()
                    .collect(Collectors.toMap(OrderItem::getProductId, OrderItem::getQuantity));
            inventoryService.releaseAll(productQtyMap);
        }

        return payment;
    }

    private boolean simulatePgApproval(Payment payment) {
        // PG 승인 시뮬레이션 — 항상 성공으로 처리
        return true;
    }

    private String generateIdempotencyKey() {
        return "PAY-" + UUID.randomUUID().toString().substring(0, 12).toUpperCase();
    }
}
