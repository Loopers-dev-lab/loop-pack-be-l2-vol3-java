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
import com.loopers.domain.payment.PaymentResult;
import com.loopers.domain.payment.PaymentService;
import com.loopers.domain.payment.PgApproveRequest;
import com.loopers.domain.point.PointAccount;
import com.loopers.domain.point.PointService;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductService;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.CouponErrorType;
import com.loopers.support.error.OrderErrorType;
import com.loopers.support.error.PaymentErrorType;
import com.loopers.support.error.PointErrorType;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

    private static final Logger log = LoggerFactory.getLogger(PaymentFacade.class);

    private final OrderService orderService;
    private final PaymentService paymentService;
    private final InventoryService inventoryService;
    private final PointService pointService;
    private final CouponService couponService;
    private final ProductService productService;
    private final OrderCacheManager orderCacheManager;

    private final org.springframework.transaction.support.TransactionTemplate txTemplate;

    public PaymentFacade(OrderService orderService, PaymentService paymentService,
                         InventoryService inventoryService, PointService pointService,
                         CouponService couponService, ProductService productService,
                         OrderCacheManager orderCacheManager,
                         org.springframework.transaction.PlatformTransactionManager txManager) {
        this.orderService = orderService;
        this.paymentService = paymentService;
        this.inventoryService = inventoryService;
        this.pointService = pointService;
        this.couponService = couponService;
        this.productService = productService;
        this.orderCacheManager = orderCacheManager;
        this.txTemplate = new org.springframework.transaction.support.TransactionTemplate(txManager);
        this.txTemplate.setTimeout(30);
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
     * TX 분리 전략:
     * TX1: 주문 검증 + Payment 생성 → 커밋
     * TX 밖: PaymentService.requestPayment() — PG 호출 + 결과 해석
     * TX2-성공: Payment 승인 + 재고 확정 + 쿠폰 사용 + 포인트 차감 + 주문 확정 + 포인트 적립
     * TX2-실패: Payment FAILED + 재고 복구
     */
    public PaymentRequestResult requestPayment(Long orderId, Long userId, String paymentMethod,
                                                Long issuedCouponId) {
        // TX1: 주문 검증 + Payment 생성
        PaymentContext context = txTemplate.execute(status -> {
            Order order = orderService.getOrder(orderId, userId);
            order.validateOwnership(userId);

            if (order.getStatus() != OrderStatus.PENDING) {
                throw new CoreException(OrderErrorType.INVALID_ORDER_STATUS);
            }

            for (OrderItem item : order.getItems()) {
                Product product = productService.getById(item.getProductId());
                if (product.getBasePrice() != item.getUnitPrice()) {
                    throw new CoreException(PaymentErrorType.PRICE_CHANGED);
                }
            }

            Payment payment = paymentService.create(
                    orderId, order.getTotalAmount(), paymentMethod, generateIdempotencyKey());

            Map<Long, Integer> productQtyMap = order.getItems().stream()
                    .collect(Collectors.toMap(OrderItem::getProductId, OrderItem::getQuantity));

            return new PaymentContext(
                    order.getId(), order.getOrderNumber(), userId, payment.getId(), order.getTotalAmount(),
                    order.getPointUsedAmount(), paymentMethod, issuedCouponId, productQtyMap);
        });

        // TX 밖: PG 호출 (PaymentService가 PG 호출 + 결과 해석을 캡슐화)
        PgApproveRequest pgRequest = new PgApproveRequest(
                userId, context.orderNumber(), paymentMethod,
                "0000-0000-0000-0000", context.totalAmount(), null);
        Payment payment = paymentService.getById(context.paymentId());
        PaymentResult pgResult = paymentService.requestPayment(payment, pgRequest);

        if (pgResult.isApproved()) {
            // TX2: 결제 확정
            txTemplate.executeWithoutResult(status -> {
                paymentService.approve(context.paymentId(), pgResult.transactionKey(), context.totalAmount());

                inventoryService.commitAll(context.productQtyMap());

                if (context.pointUsedAmount() > 0) {
                    pointService.use(userId, context.pointUsedAmount());
                }
                if (context.issuedCouponId() != null) {
                    couponService.use(context.issuedCouponId(), userId, orderId);
                }

                orderService.confirm(orderId, context.paymentId(), paymentMethod);
                pointService.earn(userId, context.totalAmount());
                orderCacheManager.registerEvictAfterCommit(userId);
            });
        } else {
            // 실패 또는 UNKNOWN — 보상
            txTemplate.executeWithoutResult(status -> {
                if (pgResult.isUnknown()) {
                    paymentService.markUnknown(context.paymentId());
                } else {
                    paymentService.fail(context.paymentId());
                }
                inventoryService.releaseAll(context.productQtyMap());
            });
        }

        Payment updatedPayment = paymentService.getById(context.paymentId());
        return new PaymentRequestResult(
                updatedPayment.getId(), updatedPayment.getOrderId(), updatedPayment.getStatus().name(),
                updatedPayment.getPaymentMethod(), updatedPayment.getRequestedAmount(),
                updatedPayment.getApprovedAmount(), updatedPayment.getPgTxnId(), updatedPayment.getApprovedAt());
    }

    private record PaymentContext(
            Long orderId, String orderNumber, Long userId, Long paymentId, int totalAmount,
            int pointUsedAmount, String paymentMethod, Long issuedCouponId,
            Map<Long, Integer> productQtyMap) {}

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
