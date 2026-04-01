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
import com.loopers.domain.payment.CompensationDlq;
import com.loopers.domain.payment.CompensationDlqRepository;
import com.loopers.domain.payment.Payment;
import com.loopers.domain.payment.PaymentResult;
import com.loopers.domain.payment.PaymentService;
import com.loopers.domain.payment.PaymentStatus;
import com.loopers.domain.payment.PgApproveRequest;
import com.loopers.domain.payment.PgServerException;
import com.loopers.domain.payment.PgTimeoutException;
import com.loopers.domain.point.PointAccount;
import com.loopers.domain.point.PointService;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductService;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.CouponErrorType;
import com.loopers.support.error.OrderErrorType;
import com.loopers.support.error.PaymentErrorType;
import com.loopers.support.error.PointErrorType;
import com.loopers.infrastructure.outbox.OutboxEventService;
import io.github.resilience4j.bulkhead.BulkheadFullException;
import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 결제 Facade (F-12, F-13, 콜백 처리)
 *
 * Resilience4j 배치:
 * - @CircuitBreaker: PG 장애 패턴 감지 → OPEN 시 TX1 전에 즉시 차단
 * - @Bulkhead: PG 호출 동시 수 제한 → 톰캣 스레드 보호
 * - Fallback: CB OPEN/Bulkhead 거절 시 즉시 실패, PG 장애 시 보상 실행
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
    private final OutboxEventService outboxEventService;
    private final CompensationDlqRepository compensationDlqRepository;

    private final org.springframework.transaction.support.TransactionTemplate txTemplate;

    public PaymentFacade(OrderService orderService, PaymentService paymentService,
                         InventoryService inventoryService, PointService pointService,
                         CouponService couponService, ProductService productService,
                         OrderCacheManager orderCacheManager,
                         OutboxEventService outboxEventService,
                         CompensationDlqRepository compensationDlqRepository,
                         org.springframework.transaction.PlatformTransactionManager txManager) {
        this.orderService = orderService;
        this.paymentService = paymentService;
        this.inventoryService = inventoryService;
        this.pointService = pointService;
        this.couponService = couponService;
        this.productService = productService;
        this.orderCacheManager = orderCacheManager;
        this.outboxEventService = outboxEventService;
        this.compensationDlqRepository = compensationDlqRepository;
        this.txTemplate = new org.springframework.transaction.support.TransactionTemplate(txManager);
        this.txTemplate.setTimeout(30);
    }

    /**
     * 할인 적용 (F-12)
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
     * 결제 요청 (F-13) — CB/Bulkhead → TX1 선차감 → PG 호출 → 비동기 콜백 대기
     *
     * @CircuitBreaker: 메서드 진입 전에 서킷 상태 체크. OPEN이면 TX1 전에 즉시 차단.
     * @Bulkhead: 동시 20개 초과 시 즉시 차단.
     *
     * PG 장애 시 예외 전파:
     *   PgServerException (500) → CB record → fallback에서 보상
     *   PgTimeoutException (조회도 실패) → CB record → fallback에서 보상
     *   PgClientException (4xx) → PaymentService에서 소화 → 정상 흐름에서 보상
     */
    @CircuitBreaker(name = "pgPayment", fallbackMethod = "fallbackRequestPayment")
    @Bulkhead(name = "pgPayment")
    public PaymentRequestResult requestPayment(Long orderId, Long userId, String paymentMethod,
                                                Long issuedCouponId, String cardNo) {
        // TX1: 주문 검증 + 선차감 + Payment 생성
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

            // 선차감: 쿠폰 사용 (원자적 UPDATE)
            if (issuedCouponId != null) {
                couponService.use(issuedCouponId, userId, orderId);
            }

            // 선차감: 포인트 차감 (원자적 UPDATE)
            if (order.getPointUsedAmount() > 0) {
                pointService.use(userId, order.getPointUsedAmount());
            }

            Payment payment = paymentService.create(
                    orderId, order.getTotalAmount(), paymentMethod, generateIdempotencyKey());


            Map<Long, Integer> productQtyMap = order.getItems().stream()
                    .collect(Collectors.toMap(OrderItem::getProductId, OrderItem::getQuantity));

            return new PaymentContext(
                    order.getId(), order.getOrderNumber(), userId, payment.getId(), order.getTotalAmount(),
                    order.getPointUsedAmount(), paymentMethod, issuedCouponId, productQtyMap);
        });

        // TX 밖: PG 호출 (PgServerException / PgTimeoutException → fallback으로 전파)
        PgApproveRequest pgRequest = new PgApproveRequest(
                userId, context.orderNumber(), paymentMethod,
                cardNo, context.totalAmount(), null);
        Payment payment = paymentService.getById(context.paymentId());
        PaymentResult pgResult = paymentService.requestPayment(payment, pgRequest);

        // 즉시 처리 가능한 경우
        if (pgResult.isApproved()) {
            confirmPayment(context.orderId(), pgResult.transactionKey());
        } else if (pgResult.isFailed()) {
            compensatePayment(context.orderId());
        } else if (pgResult.isUnknown()) {
            // UNKNOWN — 보상하지 않고 대기 (콜백 또는 대사 배치에서 처리)
            txTemplate.executeWithoutResult(s -> paymentService.markUnknown(context.paymentId()));
        }
        // PENDING — 콜백 대기 (아무것도 안 함)

        Payment updatedPayment = paymentService.getById(context.paymentId());
        return new PaymentRequestResult(
                updatedPayment.getId(), updatedPayment.getOrderId(), updatedPayment.getStatus().name(),
                updatedPayment.getPaymentMethod(), updatedPayment.getRequestedAmount(),
                updatedPayment.getApprovedAmount(), updatedPayment.getPgTxnId(), updatedPayment.getApprovedAt());
    }

    /**
     * Fallback — CB OPEN / Bulkhead 거절 / PG 인프라 장애 시 호출
     *
     * 4가지 시나리오:
     * 1. CoreException (비즈니스 예외): CB가 ignore했지만 Bulkhead fallback에 잡힘 → re-throw
     * 2. CallNotPermittedException (CB OPEN): TX1 미실행 → 보상 불필요 → 즉시 실패
     * 3. BulkheadFullException (동시 호출 초과): TX1 미실행 → 보상 불필요 → 즉시 실패
     * 4. PgServerException/PgTimeoutException (PG 장애): TX1 커밋됨 → 보상 실행
     */
    private PaymentRequestResult fallbackRequestPayment(Long orderId, Long userId, String paymentMethod,
                                                         Long issuedCouponId, String cardNo, Throwable t) {
        // CB가 ignore한 비즈니스 예외 → 그대로 re-throw (원래 HTTP 상태 코드 유지)
        if (t instanceof CoreException ce) {
            throw ce;
        }

        if (t instanceof CallNotPermittedException) {
            log.warn("[CB OPEN] PG 호출 차단 — orderId={}", orderId);
            return new PaymentRequestResult(null, orderId, "FAILED", paymentMethod, 0, null, null, null);
        }

        if (t instanceof BulkheadFullException) {
            log.warn("[Bulkhead Full] PG 동시 호출 초과 — orderId={}", orderId);
            return new PaymentRequestResult(null, orderId, "FAILED", paymentMethod, 0, null, null, null);
        }

        // PG 인프라 장애 — TX1 커밋됨 → 보상 필요
        log.warn("[PG 장애] 결제 실패 — 보상 실행: orderId={}, cause={}", orderId, t.getMessage());
        compensatePayment(orderId);

        try {
            Payment payment = paymentService.getByOrderId(orderId);
            return new PaymentRequestResult(
                    payment.getId(), orderId, payment.getStatus().name(),
                    payment.getPaymentMethod(), payment.getRequestedAmount(),
                    payment.getApprovedAmount(), payment.getPgTxnId(), payment.getApprovedAt());
        } catch (Exception e) {
            log.error("Fallback 응답 생성 실패 — orderId={}", orderId, e);
            return new PaymentRequestResult(null, orderId, "FAILED", paymentMethod, 0, null, null, null);
        }
    }

    /**
     * PG 콜백 처리 — CB를 거치지 않는다 (콜백은 PG가 보내는 것이므로 CB 차단 대상 아님)
     */
    public void processCallback(String transactionKey, String callbackOrderNumber, Long callbackUserId) {
        Order order = orderService.getByOrderNumber(callbackOrderNumber);
        Long userId = callbackUserId != null ? callbackUserId : order.getUserId();

        Payment payment = paymentService.getByOrderId(order.getId());
        if (payment.getStatus() != PaymentStatus.REQUESTED && payment.getStatus() != PaymentStatus.UNKNOWN) {
            log.info("이미 처리된 결제 — 콜백 무시: paymentId={}, status={}", payment.getId(), payment.getStatus());
            return;
        }

        PaymentResult verifiedResult = paymentService.verifyCallback(transactionKey, userId);

        if (verifiedResult.isPending()) {
            log.warn("PG 콜백 도착했으나 PG 상태가 아직 PENDING — 무시: txnKey={}", transactionKey);
            return;
        }
        if (verifiedResult.isUnknown()) {
            log.warn("PG 콜백 검증 실패 — 대사 배치에서 처리: txnKey={}", transactionKey);
            return;
        }

        if (verifiedResult.isApproved()) {
            confirmPayment(order.getId(), verifiedResult.transactionKey());
        } else if (verifiedResult.isFailed()) {
            compensatePayment(order.getId());
        }
    }

    /**
     * TX2: 결제 확정
     */
    public void confirmPayment(Long orderId, String pgTxnId) {
        txTemplate.executeWithoutResult(status -> {
            Payment payment = paymentService.getByOrderId(orderId);

            if (payment.getStatus() == PaymentStatus.APPROVED) {
                log.info("이미 승인된 결제 — 확정 스킵: orderId={}", orderId);
                return;
            }

            Order order = orderService.getById(orderId);

            Map<Long, Integer> productQtyMap = order.getItems().stream()
                    .collect(Collectors.toMap(OrderItem::getProductId, OrderItem::getQuantity, Integer::sum));

            paymentService.approve(payment.getId(), pgTxnId, order.getTotalAmount());
            inventoryService.commitAll(productQtyMap);
            orderService.confirm(orderId, payment.getId(), payment.getPaymentMethod());
            pointService.earn(order.getUserId(), order.getTotalAmount());

            // Outbox 저장 — 같은 TX (판매량 집계 → catalog-events-v1)
            for (var entry : productQtyMap.entrySet()) {
                outboxEventService.save("PRODUCT", entry.getKey(),
                        "OrderItemSoldEvent",
                        new com.loopers.domain.common.event.OrderItemSoldEvent(
                                orderId, Map.of(entry.getKey(), entry.getValue())),
                        "catalog-events-v1", String.valueOf(entry.getKey()));
            }

            orderCacheManager.registerEvictAfterCommit(order.getUserId());
        });
    }

    /**
     * 보상 트랜잭션 — 실패 시 500ms 후 1회 재시도, 재시도도 실패하면 DLQ 저장
     */
    public void compensatePayment(Long orderId) {
        try {
            executeCompensation(orderId);
        } catch (Exception firstFailure) {
            log.warn("보상 트랜잭션 1차 실패 — 즉시 재시도 (orderId={})", orderId, firstFailure);

            try {
                executeCompensation(orderId);
            } catch (Exception secondFailure) {
                log.error("보상 트랜잭션 2차 실패 — DLQ 저장 (orderId={})", orderId, secondFailure);
                saveToDlq(orderId, secondFailure.getMessage());
            }
        }
    }

    private void executeCompensation(Long orderId) {
        txTemplate.executeWithoutResult(status -> {
            Payment payment = paymentService.getByOrderId(orderId);

            if (payment.getStatus() == PaymentStatus.FAILED || payment.getStatus() == PaymentStatus.APPROVED) {
                log.info("이미 처리된 결제 — 보상 스킵: orderId={}, status={}", orderId, payment.getStatus());
                return;
            }

            Order order = orderService.getById(orderId);
            paymentService.fail(payment.getId());

            if (order.getCouponId() != null) {
                couponService.restore(order.getCouponId(), orderId);
            }
            if (order.getPointUsedAmount() > 0) {
                pointService.refund(order.getUserId(), order.getPointUsedAmount());
            }

            Map<Long, Integer> productQtyMap = order.getItems().stream()
                    .collect(Collectors.toMap(OrderItem::getProductId, OrderItem::getQuantity, Integer::sum));
            inventoryService.releaseAll(productQtyMap);

            orderService.cancel(orderId, order.getUserId());

            orderCacheManager.registerEvictAfterCommit(order.getUserId());
        });
    }

    private void saveToDlq(Long orderId, String failureReason) {
        try {
            Payment payment = paymentService.getByOrderId(orderId);
            CompensationDlq dlq = CompensationDlq.create(orderId, payment.getId(), failureReason);
            compensationDlqRepository.save(dlq);
            log.error("CRITICAL: 보상 실패 → DLQ 저장 완료 (orderId={}, paymentId={})", orderId, payment.getId());
        } catch (Exception dlqFailure) {
            log.error("CRITICAL: DLQ 저장도 실패 — 수동 복구 필요 (orderId={})", orderId, dlqFailure);
        }
    }

    public void retryPendingCompensations() {
        List<CompensationDlq> pendingItems = compensationDlqRepository.findAllPending();

        for (CompensationDlq dlq : pendingItems) {
            if (!dlq.isRetryable()) {
                continue;
            }

            try {
                executeCompensation(dlq.getOrderId());
                dlq.markCompleted();
                compensationDlqRepository.save(dlq);
                log.info("DLQ 보상 재시도 성공: orderId={}, retryCount={}", dlq.getOrderId(), dlq.getRetryCount());
            } catch (Exception e) {
                dlq.markRetried(e.getMessage());
                compensationDlqRepository.save(dlq);

                if (!dlq.isRetryable()) {
                    log.error("CRITICAL: DLQ 최대 재시도 초과 — 수동 개입 필요 (orderId={}, retryCount={})",
                            dlq.getOrderId(), dlq.getRetryCount());
                } else {
                    log.warn("DLQ 보상 재시도 실패 — 다음 배치에서 재시도 (orderId={}, retryCount={}/{})",
                            dlq.getOrderId(), dlq.getRetryCount(), dlq.getMaxRetries());
                }
            }
        }
    }

    /**
     * Payment 생성 — OrderFacade의 TX1 안에서 호출된다 (REQUIRED 전파로 기존 트랜잭션 합류)
     */
    public Long createPaymentForOrder(Long orderId, int totalAmount, String paymentMethod) {
        Payment payment = paymentService.create(orderId, totalAmount, paymentMethod, generateIdempotencyKey());
        return payment.getId();
    }

    /**
     * 주문 결제 처리 — OrderFacade에서 TX1 커밋 후 호출
     *
     * PG 호출 + 결과 해석 + 확정/보상을 PaymentFacade가 캡슐화한다.
     * OrderFacade는 PG 관련 로직(PgApproveRequest, PaymentResult 해석)을 알 필요 없다.
     */
    public PaymentRequestResult processPaymentForOrder(Long orderId, Long userId,
                                                        String paymentMethod, String cardNo) {
        Order order = orderService.getById(orderId);
        Payment payment = paymentService.getByOrderId(orderId);

        try {
            PgApproveRequest pgRequest = new PgApproveRequest(
                    userId, order.getOrderNumber(), paymentMethod,
                    cardNo, payment.getRequestedAmount(), null);
            PaymentResult pgResult = paymentService.requestPayment(payment, pgRequest);

            if (pgResult.isApproved()) {
                confirmPayment(orderId, pgResult.transactionKey());
            } else if (pgResult.isFailed()) {
                compensatePayment(orderId);
            } else if (pgResult.isUnknown()) {
                txTemplate.executeWithoutResult(s -> paymentService.markUnknown(payment.getId()));
                log.warn("PG 결제 결과 불확실 — 콜백/대사 배치 대기 (orderId={})", orderId);
            }
            // PENDING — 콜백 대기
        } catch (Exception e) {
            log.warn("[PG 장애] 주문 결제 실패 — 보상 실행: orderId={}, cause={}", orderId, e.getMessage());
            compensatePayment(orderId);
            throw e;
        }

        Payment updatedPayment = paymentService.getById(payment.getId());
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
