package com.loopers.application.payment;

import com.loopers.domain.order.OrderItemModel;
import com.loopers.domain.order.OrderModel;
import com.loopers.domain.order.OrderService;
import com.loopers.domain.outbox.DomainEvents;
import com.loopers.domain.outbox.TransactionalOutboxWriter;
import com.loopers.domain.order.OrderRepository;
import com.loopers.domain.payment.PaymentModel;
import com.loopers.domain.payment.PaymentRepository;
import com.loopers.domain.payment.PaymentStatus;
import com.loopers.infrastructure.payment.PgPaymentStatusResponse;
import com.loopers.infrastructure.payment.PgSimulatorClient;
import com.loopers.infrastructure.payment.PgSimulatorRequest;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import feign.FeignException;
import feign.RetryableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.time.Duration;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 결제 유스케이스 조율 (06 §10.2).
 * (1) DB 트랜잭션으로 PENDING 저장 (2) 트랜잭션 종료 후 PG 호출.
 * (3) Phase 3: 콜백 처리 handleCallback.
 * (4) Phase 8: PG 조회 API로 PENDING 동기화 {@link #recoverPendingFromPgSimulator(Long)}.
 */
@Service
public class PaymentFacade {

    private static final Logger log = LoggerFactory.getLogger(PaymentFacade.class);

    private final PaymentPersistenceService persistenceService;
    private final PgPaymentRequester pgPaymentRequester;
    private final PgSimulatorClient pgSimulatorClient;
    private final ObjectProvider<PaymentFacade> paymentFacadeSelf;
    private final String callbackUrl;
    private final String callbackSecret;
    private final OrderService orderService;
    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final Environment environment;
    private final Duration pendingMinAge;
    private final int staleBatchSize;
    private final TransactionalOutboxWriter transactionalOutboxWriter;

    public PaymentFacade(PaymentPersistenceService persistenceService,
                         @Value("${pg.simulator.callback-url}") String callbackUrl,
                         @Value("${pg.simulator.callback-secret:}") String callbackSecret,
                         OrderService orderService,
                         OrderRepository orderRepository,
                         PaymentRepository paymentRepository,
                         PgPaymentRequester pgPaymentRequester,
                         PgSimulatorClient pgSimulatorClient,
                         ObjectProvider<PaymentFacade> paymentFacadeSelf,
                         Environment environment,
                         @Value("${payment.recovery.pending-min-age:5m}") Duration pendingMinAge,
                         @Value("${payment.recovery.stale-batch-size:500}") int staleBatchSize,
                         TransactionalOutboxWriter transactionalOutboxWriter) {
        this.persistenceService = persistenceService;
        this.pgPaymentRequester = pgPaymentRequester;
        this.pgSimulatorClient = pgSimulatorClient;
        this.paymentFacadeSelf = paymentFacadeSelf;
        this.callbackUrl = callbackUrl;
        this.callbackSecret = callbackSecret != null ? callbackSecret : "";
        this.orderService = orderService;
        this.orderRepository = orderRepository;
        this.paymentRepository = paymentRepository;
        this.environment = environment;
        this.pendingMinAge = pendingMinAge;
        this.staleBatchSize = staleBatchSize;
        this.transactionalOutboxWriter = transactionalOutboxWriter;
    }

    /**
     * 콜백 발신 주체 검증 (06-payment-change-issues §4.1). 시크릿이 설정된 경우에만 검사.
     */
    public void verifyCallbackSecret(String headerSecret) {
        if (callbackSecret.isEmpty()) {
            if (isProductionProfile()) {
                log.error("PG callback secret is not configured in production");
                throw new CoreException(ErrorType.UNAUTHORIZED, "콜백 인증에 실패했습니다.");
            }
            return;
        }
        if (headerSecret == null || !callbackSecret.equals(headerSecret)) {
            throw new CoreException(ErrorType.UNAUTHORIZED, "콜백 인증에 실패했습니다.");
        }
    }

    /**
     * 결제 요청. Phase 2: PENDING 저장 후 트랜잭션 밖에서 PG 호출.
     * Retry/CB/Fallback은 Phase 4~6에서 적용.
     */
    public PaymentInfo requestPayment(Long userId, Long orderId, String cardType, String cardNo) {
        PendingPaymentResult result = persistenceService.savePendingAndGetRequestParam(
                userId, orderId, cardType, cardNo, callbackUrl);
        // 트랜잭션 밖: PG 호출 (06 §5.1). 실패 시에도 200 + PENDING 반환 (06-payment-change-issues §3.1).
        PaymentRequestParam param = result.requestParam();
        PgSimulatorRequest request = new PgSimulatorRequest(
                param.orderId(),
                param.cardType(),
                param.cardNo(),
                param.amount(),
                param.callbackUrl()
        );
        try {
            pgPaymentRequester.requestPaymentToPg(request);
        } catch (Exception e) {
            // PENDING 저장은 이미 커밋됨. Feign/네트워크 계열만 흡수하고, 그 외는 로깅 후 전파해 장애 탐지.
            if (isExternalOrNetworkFailure(e)) {
                log.warn("PG 호출 실패 - PENDING 유지 orderId={} reason={}", orderId, e.toString(), e);
            } else {
                log.error("PG 호출 중 예상치 못한 오류 - orderId={}", orderId, e);
                throw e;
            }
        }
        return result.paymentInfo();
    }

    /**
     * PG 콜백 처리 (06 §3, §9). PENDING 결제를 먼저 조회한 뒤 처리 (06-payment-change-issues §3.2).
     * 없으면 이미 처리된 건으로 멱등 반환.
     */
    @Transactional
    public void handleCallback(PaymentCallbackParam param) {
        var pendingOpt = paymentRepository.findTopPendingByOrderIdForUpdate(param.orderId());

        if (pendingOpt.isEmpty()) {
            return;
        }
        PaymentModel payment = pendingOpt.get();

        if (param.success()) {
            if (param.amount() != null) {
                OrderModel order = orderRepository.findById(param.orderId())
                        .orElse(null);
                if (order != null) {
                    long orderAmountWon = order.getFinalAmount().setScale(0, RoundingMode.HALF_UP).longValue();
                    if (param.amount() != orderAmountWon) {
                        log.warn("콜백 금액 불일치 orderId={} pgAmount={} orderAmount={}", param.orderId(), param.amount(), orderAmountWon);
                        payment.markFailed();
                        paymentRepository.save(payment);
                        return;
                    }
                }
            }
            OrderModel paidOrder = orderService.completePayment(param.orderId());
            try {
                payment.markSuccess(param.pgTransactionId());
                paymentRepository.save(payment);
            } catch (IllegalStateException ex) {
                log.info("결제 SUCCESS 전이 스킵(멱등) orderId={} message={}", param.orderId(), ex.getMessage());
                return;
            }
            appendPaymentCompletedOutbox(paidOrder);
        } else {
            try {
                payment.markFailed();
                paymentRepository.save(payment);
            } catch (IllegalStateException ex) {
                log.info("결제 FAILED 전이 스킵(멱등) orderId={} message={}", param.orderId(), ex.getMessage());
            }
        }
    }

    private void appendPaymentCompletedOutbox(OrderModel order) {
        List<Map<String, Object>> lines = new ArrayList<>();
        for (OrderItemModel item : order.getOrderItems()) {
            Map<String, Object> line = new LinkedHashMap<>();
            line.put("productId", item.getProductId());
            line.put("quantity", item.getQuantity());
            lines.add(line);
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("orderId", order.getId());
        payload.put("occurredAt", Instant.now().toString());
        payload.put("lines", lines);
        transactionalOutboxWriter.record(
                DomainEvents.Topic.ORDER_EVENTS,
                String.valueOf(order.getId()),
                DomainEvents.Type.PAYMENT_COMPLETED,
                payload);
    }

    /**
     * 콜백 미수신 시 PG 주문별 조회로 PENDING 건을 동기화한다 (06 §11.3~11.4, Phase 8).
     * {@link #handleCallback(PaymentCallbackParam)}는 프록시를 통해 호출되어 트랜잭션이 적용된다.
     */
    public void recoverPendingFromPgSimulator(Long orderId) {
        if (orderId == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "orderId는 필수입니다.");
        }
        boolean hasPending = paymentRepository.findTopByOrderIdOrderByCreatedAtDesc(orderId)
                .filter(PaymentModel::isPending)
                .isPresent();
        if (!hasPending) {
            return;
        }
        PgPaymentStatusResponse pg;
        try {
            pg = pgSimulatorClient.getPaymentsByOrderId(orderId);
        } catch (Exception e) {
            if (isExternalOrNetworkFailure(e)) {
                log.warn("PG 주문별 조회 실패 orderId={}", orderId, e);
            } else {
                log.error("PG 주문별 조회 중 예상치 못한 오류 orderId={}", orderId, e);
            }
            return;
        }
        if (pg == null) {
            log.warn("PG 주문별 조회 응답이 비어 있음 orderId={}, PENDING을 TIMEOUT 처리", orderId);
            paymentFacadeSelf.getObject().timeoutPendingPaymentForOrder(orderId);
            return;
        }
        Long amountForCallback = pg.amount();
        if (amountForCallback == null) {
            amountForCallback = orderRepository.findById(orderId)
                    .map(o -> o.getFinalAmount().setScale(0, RoundingMode.HALF_UP).longValue())
                    .orElse(null);
        }
        PaymentFacade facade = paymentFacadeSelf.getObject();
        if (pg.isSuccessful()) {
            facade.handleCallback(new PaymentCallbackParam(
                    orderId, true, pg.paymentId(), pg.failureReason(), amountForCallback));
        } else if (Boolean.FALSE.equals(pg.success())) {
            facade.handleCallback(new PaymentCallbackParam(
                    orderId, false, pg.paymentId(), pg.failureReason(), pg.amount()));
        } else {
            log.warn("PG 주문별 조회 success 값 비정상(null) orderId={}, PENDING을 TIMEOUT 처리", orderId);
            facade.timeoutPendingPaymentForOrder(orderId);
        }
    }

    /**
     * 콜백 유실 등으로 오래 PENDING으로 남은 건만 PG에서 동기화한다 (Phase 8 배치).
     * <p>
     * 외부 호출을 오래 트랜잭션에 묶지 않기 위해, 여기서는 배치 대상 조회만 DB에서 수행한다.
     */
    public void recoverStalePendingPayments() {
        ZonedDateTime cutoff = ZonedDateTime.now().minus(pendingMinAge);
        List<PaymentModel> stalePayments = paymentRepository
                .findStalePendingPayments(PaymentStatus.PENDING, cutoff, staleBatchSize);
        if (stalePayments.isEmpty()) {
            return;
        }
        for (PaymentModel payment : stalePayments) {
            recoverPendingFromPgSimulator(payment.getOrderId());
        }
    }

    /**
     * PG에 결제 기록이 없을 때(응답 null 등) PENDING을 TIMEOUT으로 정리한다 (06 §11.4 미접수 근사).
     */
    @Transactional
    public void timeoutPendingPaymentForOrder(Long orderId) {
        paymentRepository.findTopByOrderIdOrderByCreatedAtDesc(orderId)
                .filter(PaymentModel::isPending)
                .ifPresent(p -> {
                    p.markTimeout();
                    paymentRepository.save(p);
                });
    }

    private boolean isProductionProfile() {
        for (String profile : environment.getActiveProfiles()) {
            if ("prd".equalsIgnoreCase(profile)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Feign/HTTP/IO 계열은 PG 장애로 간주하고 PENDING 유지 흐름에 맡긴다. 그 외는 내부 버그 가능성이 있다.
     */
    private static boolean isExternalOrNetworkFailure(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof FeignException || t instanceof RetryableException || t instanceof IOException) {
                return true;
            }
        }
        return false;
    }
}
