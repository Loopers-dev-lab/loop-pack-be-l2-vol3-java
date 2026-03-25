package com.loopers.application.payment;

import com.loopers.application.cache.OrderCacheManager;
import com.loopers.domain.coupon.CouponService;
import com.loopers.domain.coupon.CouponTemplate;
import com.loopers.domain.coupon.IssuedCoupon;
import com.loopers.domain.inventory.InventoryService;
import com.loopers.domain.order.Order;
import com.loopers.domain.order.OrderItem;
import com.loopers.domain.order.OrderService;
import com.loopers.domain.order.OrderStatus;
import com.loopers.domain.payment.Payment;
import com.loopers.domain.payment.PaymentService;
import com.loopers.domain.point.PointAccount;
import com.loopers.domain.point.PointService;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductService;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.CouponErrorType;
import com.loopers.support.error.OrderErrorType;
import com.loopers.support.error.PaymentErrorType;
import com.loopers.support.error.PointErrorType;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;
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
    private final ProductService productService;
    private final OrderCacheManager orderCacheManager;
    private final ApplicationEventPublisher eventPublisher;

    public PaymentFacade(OrderService orderService, PaymentService paymentService,
                         InventoryService inventoryService, PointService pointService,
                         CouponService couponService, ProductService productService,
                         OrderCacheManager orderCacheManager,
                         ApplicationEventPublisher eventPublisher) {
        this.orderService = orderService;
        this.paymentService = paymentService;
        this.inventoryService = inventoryService;
        this.pointService = pointService;
        this.couponService = couponService;
        this.productService = productService;
        this.orderCacheManager = orderCacheManager;
        this.eventPublisher = eventPublisher;
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
    public DiscountAppliedResult applyDiscount(Long orderId, Long userId, Long issuedCouponId, int pointAmount) {
        Order order = orderService.getOrder(orderId, userId);

        int discountAmount = 0;
        if (issuedCouponId != null) {
            IssuedCoupon issuedCoupon = couponService.getIssuedCoupon(issuedCouponId, userId);

            CouponTemplate template = couponService.getTemplate(issuedCoupon.getCouponTemplateId());
            if (!template.isApplicable(order.getSubtotalAmount(), ZonedDateTime.now())) {
                throw new CoreException(CouponErrorType.INVALID_TEMPLATE);
            }
            discountAmount = template.calculateDiscount(order.getSubtotalAmount());
        }

        if (pointAmount > 0) {
            // 잔액 검증만 — 실제 차감은 결제 성공 시
            PointAccount account = pointService.getAccount(userId);
            if (account.getBalance() < pointAmount) {
                throw new CoreException(PointErrorType.INSUFFICIENT_BALANCE);
            }
        }

        order.applyDiscount(discountAmount, pointAmount, 0, issuedCouponId);
        return new DiscountAppliedResult(
                order.getId(), order.getSubtotalAmount(), order.getDiscountAmount(),
                order.getPointUsedAmount(), order.getShippingFee(), order.getTotalAmount());
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
    public PaymentRequestResult requestPayment(Long orderId, Long userId, String paymentMethod,
                                                Long issuedCouponId) {
        Order order = orderService.getOrder(orderId, userId);
        order.validateOwnership(userId);

        if (order.getStatus() != OrderStatus.PENDING) {
            throw new CoreException(OrderErrorType.INVALID_ORDER_STATUS);
        }

        // 결제 시점 가격 재검증 — 주문 생성 후 상품 가격이 변경되었는지 확인
        for (OrderItem item : order.getItems()) {
            Product product = productService.getById(item.getProductId());
            if (product.getBasePrice() != item.getUnitPrice()) {
                throw new CoreException(PaymentErrorType.PRICE_CHANGED);
            }
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

            // 주문 상태 변경(PENDING → PAID) → afterCommit에서 캐시 삭제
            orderCacheManager.registerEvictAfterCommit(userId);

            // 도메인 이벤트 발행 — 같은 @Transactional 안에서 발행
            // BEFORE_COMMIT 리스너가 같은 TX에서 Outbox 저장 → 비즈니스 + Outbox 원자성 보장
            eventPublisher.publishEvent(new com.loopers.domain.common.event.OrderConfirmedEvent(
                    orderId, userId, order.getTotalAmount(), payment.getId()));
            eventPublisher.publishEvent(new com.loopers.domain.common.event.OrderItemSoldEvent(
                    orderId, productQtyMap));
        } else {
            payment.reject();

            // reserved_qty 복구
            Map<Long, Integer> productQtyMap = order.getItems().stream()
                    .collect(Collectors.toMap(OrderItem::getProductId, OrderItem::getQuantity));
            inventoryService.releaseAll(productQtyMap);
        }

        return new PaymentRequestResult(
                payment.getId(), payment.getOrderId(), payment.getStatus().name(),
                payment.getPaymentMethod(), payment.getRequestedAmount(),
                payment.getApprovedAmount(), payment.getPgTxnId(), payment.getApprovedAt());
    }

    private boolean simulatePgApproval(Payment payment) {
        // PG 승인 시뮬레이션 — 항상 성공으로 처리
        return true;
    }

    private String generateIdempotencyKey() {
        return "PAY-" + UUID.randomUUID().toString().substring(0, 12).toUpperCase();
    }

    public record DiscountAppliedResult(
            Long orderId, int subtotalAmount, int discountAmount,
            int pointUsedAmount, int shippingFee, int totalAmount) {}

    public record PaymentRequestResult(
            Long paymentId, Long orderId, String status,
            String paymentMethod, int requestedAmount,
            Integer approvedAmount, String pgTxnId, ZonedDateTime approvedAt) {}
}
