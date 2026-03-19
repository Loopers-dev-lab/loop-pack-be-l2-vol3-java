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
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 결제 Facade (F-12, F-13, 콜백 처리)
 *
 * Order + Payment + Inventory + Point + Coupon 5개 도메인 조율.
 * - F-12: 할인 적용 (쿠폰/포인트)
 * - F-13: 결제 요청 → PG 승인 → 비동기 콜백 대기
 * - 콜백/배치: PG 결과 도착 시 TX2(확정) 또는 보상
 *
 * TX 분리 전략:
 * TX1: 주문 검증 + 쿠폰/포인트 선차감 + Payment 생성 → 커밋
 * TX 밖: PaymentService.requestPayment() — PG 호출 + 결과 해석
 * TX2-성공 (콜백): Payment 승인 + 재고 확정 + 주문 확정 + 포인트 적립
 * TX2-실패 (콜백): Payment FAILED + 쿠폰 복원 + 포인트 환급 + 재고 해제 + 주문 취소
 */
@Component
public class PaymentFacade {

    private static final Logger log = LoggerFactory.getLogger(PaymentFacade.class);

    private static final long COMPENSATION_RETRY_INTERVAL_MS = 500;

    private final OrderService orderService;
    private final PaymentService paymentService;
    private final InventoryService inventoryService;
    private final PointService pointService;
    private final CouponService couponService;
    private final ProductService productService;
    private final OrderCacheManager orderCacheManager;
    private final CompensationDlqRepository compensationDlqRepository;

    private final org.springframework.transaction.support.TransactionTemplate txTemplate;

    public PaymentFacade(OrderService orderService, PaymentService paymentService,
                         InventoryService inventoryService, PointService pointService,
                         CouponService couponService, ProductService productService,
                         OrderCacheManager orderCacheManager,
                         CompensationDlqRepository compensationDlqRepository,
                         org.springframework.transaction.PlatformTransactionManager txManager) {
        this.orderService = orderService;
        this.paymentService = paymentService;
        this.inventoryService = inventoryService;
        this.pointService = pointService;
        this.couponService = couponService;
        this.productService = productService;
        this.orderCacheManager = orderCacheManager;
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
     * 결제 요청 (F-13) — TX1 선차감 + PG 호출 + 비동기 콜백 대기
     *
     * TX1: 주문 검증 + 쿠폰 사용 + 포인트 차감 + Payment 생성 → 커밋
     * TX 밖: PG approve() → PENDING이면 콜백 대기, 즉시 실패면 보상
     */
    public PaymentRequestResult requestPayment(Long orderId, Long userId, String paymentMethod,
                                                Long issuedCouponId) {
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

        // TX 밖: PG 호출
        PgApproveRequest pgRequest = new PgApproveRequest(
                userId, context.orderNumber(), paymentMethod,
                "0000-0000-0000-0000", context.totalAmount(), null);
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
     * PG 콜백 처리 — PG 결과 검증 후 TX2(확정) 또는 보상 실행
     *
     * 1. PG 조회 API로 콜백 검증 (위조 방지)
     * 2. Payment 상태 확인 (이미 처리된 건 무시 — 멱등성)
     * 3. 검증된 결과에 따라 TX2 또는 보상
     *
     * Order/Payment 조회로 컨텍스트를 복원한다 (별도 테이블 불필요).
     */
    public void processCallback(String transactionKey, String callbackOrderNumber, Long callbackUserId) {
        // 1. Order 조회로 컨텍스트 복원 (userId를 Order에서 가져옴 — 콜백에 userId가 없을 수 있음)
        Order order = orderService.getByOrderNumber(callbackOrderNumber);
        Long userId = callbackUserId != null ? callbackUserId : order.getUserId();

        // 2. Payment 상태 확인 — 이미 처리된 건이면 무시 (멱등성)
        Payment payment = paymentService.getByOrderId(order.getId());
        if (payment.getStatus() != PaymentStatus.REQUESTED && payment.getStatus() != PaymentStatus.UNKNOWN) {
            log.info("이미 처리된 결제 — 콜백 무시: paymentId={}, status={}", payment.getId(), payment.getStatus());
            return;
        }

        // 3. PG 조회 API로 콜백 검증 (위조 방지)
        PaymentResult verifiedResult = paymentService.verifyCallback(transactionKey, userId);

        if (verifiedResult.isPending()) {
            log.warn("PG 콜백 도착했으나 PG 상태가 아직 PENDING — 무시: txnKey={}", transactionKey);
            return;
        }
        if (verifiedResult.isUnknown()) {
            log.warn("PG 콜백 검증 실패 — 대사 배치에서 처리: txnKey={}", transactionKey);
            return;
        }

        // 4. 검증된 결과에 따라 TX2 또는 보상
        if (verifiedResult.isApproved()) {
            confirmPayment(order.getId(), verifiedResult.transactionKey());
        } else if (verifiedResult.isFailed()) {
            compensatePayment(order.getId());
        }
    }

    /**
     * TX2: 결제 확정 — Payment 승인 + 재고 확정 + 주문 확정 + 포인트 적립
     *
     * OrderFacade, 콜백, 대사 배치 모두 이 메서드를 사용한다.
     *
     * @param orderId 주문 ID
     * @param pgTxnId PG 거래 ID (approve 시점 또는 콜백 검증 시점에 확보)
     */
    public void confirmPayment(Long orderId, String pgTxnId) {
        txTemplate.executeWithoutResult(status -> {
            Order order = orderService.getById(orderId);
            Payment payment = paymentService.getByOrderId(orderId);

            Map<Long, Integer> productQtyMap = order.getItems().stream()
                    .collect(Collectors.toMap(OrderItem::getProductId, OrderItem::getQuantity, Integer::sum));

            paymentService.approve(payment.getId(), pgTxnId, order.getTotalAmount());
            inventoryService.commitAll(productQtyMap);
            orderService.confirm(orderId, payment.getId(), payment.getPaymentMethod());
            pointService.earn(order.getUserId(), order.getTotalAmount());

            orderCacheManager.registerEvictAfterCommit(order.getUserId());
        });
    }

    /**
     * 보상 트랜잭션: TX1에서 선차감한 변경을 되돌린다.
     *
     * - Payment → FAILED
     * - 쿠폰 → ISSUED 복원
     * - 포인트 → 환급
     * - 재고 → 예약 해제
     * - 주문 → CANCELED
     *
     * 실패 시 500ms 후 1회 재시도. 재시도도 실패하면 DLQ 테이블에 저장한다.
     * OrderFacade, 콜백, 대사 배치 모두 이 메서드를 사용한다.
     */
    public void compensatePayment(Long orderId) {
        try {
            executeCompensation(orderId);
        } catch (Exception firstFailure) {
            log.warn("보상 트랜잭션 1차 실패 — 500ms 후 재시도 (orderId={})", orderId, firstFailure);

            try {
                Thread.sleep(COMPENSATION_RETRY_INTERVAL_MS);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
            }

            try {
                executeCompensation(orderId);
            } catch (Exception secondFailure) {
                log.error("보상 트랜잭션 2차 실패 — DLQ 저장 (orderId={})", orderId, secondFailure);
                saveToDlq(orderId, secondFailure.getMessage());
            }
        }
    }

    /**
     * 보상 트랜잭션 실행 (하나의 TX에서 전체 되돌림)
     *
     * 부분 보상 방지: 하나라도 실패하면 전체 롤백된다.
     */
    private void executeCompensation(Long orderId) {
        txTemplate.executeWithoutResult(status -> {
            Order order = orderService.getById(orderId);
            Payment payment = paymentService.getByOrderId(orderId);

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

    /**
     * DLQ 저장 — 보상 실패 건을 DB에 기록한다.
     *
     * DLQ 저장 자체도 실패하면 CRITICAL 로그만 남긴다 (DB 장애 시).
     */
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

    /**
     * DLQ 재처리 — 대사 배치에서 호출한다.
     *
     * PENDING 상태인 DLQ 항목을 하나씩 재시도한다.
     * 성공하면 COMPLETED, 최대 재시도 초과 시 FAILED.
     */
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
