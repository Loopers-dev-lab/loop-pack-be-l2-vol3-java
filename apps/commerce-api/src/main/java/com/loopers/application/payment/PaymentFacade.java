package com.loopers.application.payment;

import com.loopers.domain.order.Order;
import com.loopers.domain.order.OrderRepository;
import com.loopers.domain.order.OrderStatus;
import com.loopers.domain.payment.*;
import com.loopers.infrastructure.pg.*;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 결제 유스케이스 조율.
 *
 * <p>흐름 (Phase 2):</p>
 * <ol>
 *   <li>주문 검증 + 중복 결제 방지</li>
 *   <li>Payment(REQUESTED) 생성 + DB 저장</li>
 *   <li>수동 Retry 루프: PG 호출 → 실패 시 PG 상태 확인 → 멱등 재시도</li>
 *   <li>모든 PG 실패 → UNKNOWN 상태 저장 + "결제 확인 중" 응답</li>
 * </ol>
 *
 * <p>실행 순서: SlidingWindowRateLimiter(AOP) → Retry(수동) → CB(@CircuitBreaker on PgClient) → Feign</p>
 *
 * @see <a href="05-payment-resilience.md §6.4">수동 Retry + 멱등성 보장</a>
 * @see <a href="05-payment-resilience.md §8.7">최종 Fallback: UNKNOWN 상태</a>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PaymentFacade {

    private final PaymentRepository paymentRepository;
    private final OrderRepository orderRepository;
    private final PgRouter pgRouter;
    private final PaymentOutboxRepository outboxRepository;

    @Value("${payment.callback-url:http://localhost:8080/api/v1/payments/callback}")
    private String callbackUrl;

    @Value("${payment.retry.max-attempts:3}")
    private int maxRetryAttempts;

    @Value("${payment.retry.initial-wait-ms:500}")
    private long initialWaitMs;

    @Value("${payment.retry.backoff-multiplier:2}")
    private int backoffMultiplier;

    @Transactional
    public PaymentResult requestPayment(Long orderId, String cardType, String cardNo, int amount) {
        // 1. 주문 검증
        Order order = orderRepository.findById(orderId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "주문을 찾을 수 없습니다."));

        if (order.getStatus() == OrderStatus.PAID) {
            throw new CoreException(ErrorType.BAD_REQUEST, "이미 결제된 주문입니다.");
        }

        if (order.getStatus() == OrderStatus.CANCELLED) {
            throw new CoreException(ErrorType.BAD_REQUEST, "취소된 주문은 결제할 수 없습니다.");
        }

        // 2. 중복 결제 방지
        paymentRepository.findByOrderId(orderId).ifPresent(existing -> {
            if (!existing.getStatus().isTerminal()
                || existing.getStatus() == PaymentStatus.PAID) {
                throw new CoreException(ErrorType.CONFLICT,
                    "이미 결제가 진행 중이거나 완료된 주문입니다. paymentStatus=" + existing.getStatus());
            }
        });

        // 3. Payment(REQUESTED) + Outbox(PENDING) 같은 TX에서 생성
        PaymentModel payment = paymentRepository.save(
            PaymentModel.create(orderId, amount, cardType, cardNo));
        String outboxPayload = String.format(
            "{\"orderId\":%d,\"amount\":%d,\"cardType\":\"%s\",\"cardNo\":\"%s\"}",
            orderId, amount, cardType, cardNo);
        outboxRepository.save(PaymentOutbox.create(payment.getId(), orderId, outboxPayload));
        log.info("결제 요청 생성: paymentId={}, orderId={}", payment.getId(), orderId);

        // 4. 수동 Retry 루프 (PG 상태 확인 후 멱등 재시도)
        PgPaymentRequest pgRequest = PgPaymentRequest.of(orderId, cardType, cardNo, amount, callbackUrl);
        return executeWithRetry(payment, order, pgRequest);
    }

    /**
     * 수동 Retry 루프.
     *
     * <p>1차 실패 → PG 상태 확인 (기록 존재?) → 있으면 재시도 안 함 → 없으면 재시도.
     * 모든 시도 실패 → UNKNOWN 상태 저장 + "결제 확인 중" 응답.</p>
     *
     * <p>Phase 6: 동기 PG (Toss) 대응 — SUCCESS 즉시 반환 시 PAID 처리.</p>
     *
     * @see <a href="05-payment-resilience.md §6.4">멱등성 보장</a>
     */
    private PaymentResult executeWithRetry(PaymentModel payment, Order order, PgPaymentRequest pgRequest) {
        Exception lastException = null;
        long waitMs = initialWaitMs;

        for (int attempt = 1; attempt <= maxRetryAttempts; attempt++) {
            try {
                PgPaymentResponse pgResponse = pgRouter.requestPayment(pgRequest);

                // PG 응답 상태에 따른 분기
                return handlePgResponse(payment, order, pgResponse, attempt);

            } catch (Exception e) {
                lastException = e;
                log.warn("PG 결제 요청 실패: paymentId={}, attempt={}/{}, error={}",
                    payment.getId(), attempt, maxRetryAttempts, e.getMessage());

                // 마지막 시도가 아니면 → PG 상태 확인 후 재시도 여부 결정
                if (attempt < maxRetryAttempts) {
                    PaymentResult existingResult = checkPgStatusBeforeRetry(payment, pgRequest);
                    if (existingResult != null) {
                        return existingResult; // PG에 이미 기록 있음 → 재시도 안 함
                    }

                    // 대기 후 재시도
                    sleep(waitMs);
                    waitMs *= backoffMultiplier;
                }
            }
        }

        // 모든 시도 실패 → UNKNOWN Fallback
        return handleUnknownFallback(payment, lastException);
    }

    /**
     * PG 응답 상태별 처리.
     *
     * <ul>
     *   <li>PENDING (Simulator 비동기) → Payment PENDING, 콜백 대기</li>
     *   <li>SUCCESS (Toss 동기) → Payment PAID + Order PAID 즉시 확정</li>
     *   <li>FAILED (Toss 동기) → Payment FAILED 즉시 확정</li>
     * </ul>
     */
    private PaymentResult handlePgResponse(PaymentModel payment, Order order,
                                           PgPaymentResponse pgResponse, int attempt) {
        String pgProvider = pgResponse.pgProvider();

        switch (pgResponse.status()) {
            case "SUCCESS" -> {
                // 동기 PG (Toss): 즉시 결제 확정
                payment.markPending(pgResponse.transactionKey(), pgProvider);
                payment.markPaid();
                paymentRepository.save(payment);
                order.pay();
                orderRepository.save(order);
                log.info("결제 즉시 확정 (동기 PG): paymentId={}, transactionKey={}, provider={}, attempt={}",
                    payment.getId(), pgResponse.transactionKey(), pgProvider, attempt);
                return new PaymentResult(payment.getId(), pgResponse.transactionKey(),
                    PaymentStatus.PAID.name(), null);
            }
            case "FAILED" -> {
                // 동기 PG (Toss): 즉시 실패
                payment.markFailed("PG 결제 실패 (provider=" + pgProvider + ")");
                paymentRepository.save(payment);
                log.info("결제 즉시 실패 (동기 PG): paymentId={}, provider={}, attempt={}",
                    payment.getId(), pgProvider, attempt);
                return new PaymentResult(payment.getId(), pgResponse.transactionKey(),
                    PaymentStatus.FAILED.name(), "PG 결제 실패");
            }
            default -> {
                // PENDING (Simulator 비동기): 콜백 대기
                payment.markPending(pgResponse.transactionKey(), pgProvider);
                paymentRepository.save(payment);
                log.info("결제 PENDING: paymentId={}, transactionKey={}, provider={}, attempt={}",
                    payment.getId(), pgResponse.transactionKey(), pgProvider, attempt);
                return new PaymentResult(payment.getId(), pgResponse.transactionKey(),
                    payment.getStatus().name(), null);
            }
        }
    }

    /**
     * 재시도 전 PG 상태 확인 — 멱등성 보장.
     *
     * <p>첫 번째 요청이 PG에서 이미 처리되었을 수 있으므로,
     * orderId로 PG 상태를 확인하고 기록이 있으면 재시도하지 않는다.</p>
     *
     * @return PG에 기록이 있으면 PaymentResult, 없으면 null (재시도 필요)
     */
    private PaymentResult checkPgStatusBeforeRetry(PaymentModel payment, PgPaymentRequest pgRequest) {
        try {
            PgPaymentStatusResponse pgStatus = pgRouter.getPaymentByOrderId(
                pgRequest.orderId(), pgRouter.getPrimaryClient().getProviderName());

            if (pgStatus != null && pgStatus.transactionKey() != null
                && !"UNKNOWN".equals(pgStatus.status())) {

                log.info("PG에 기록 존재 — 재시도 안 함: paymentId={}, pgStatus={}",
                    payment.getId(), pgStatus.status());

                // PG 상태에 따라 내부 상태 전이
                return handlePgStatusResult(payment, pgStatus);
            }
        } catch (Exception e) {
            log.warn("PG 상태 확인 실패 — 재시도 진행: paymentId={}", payment.getId());
        }
        return null; // PG에 기록 없음 → 재시도 필요
    }

    /**
     * PG 상태 확인 결과를 내부 Payment 상태에 반영한다.
     */
    private PaymentResult handlePgStatusResult(PaymentModel payment, PgPaymentStatusResponse pgStatus) {
        switch (pgStatus.status()) {
            case "PENDING" -> {
                payment.markPending(pgStatus.transactionKey(),
                    pgRouter.getPrimaryClient().getProviderName());
                paymentRepository.save(payment);
                return new PaymentResult(payment.getId(), pgStatus.transactionKey(),
                    PaymentStatus.PENDING.name(), null);
            }
            case "SUCCESS" -> {
                payment.markPending(pgStatus.transactionKey(),
                    pgRouter.getPrimaryClient().getProviderName());
                payment.markPaid();
                paymentRepository.save(payment);
                return new PaymentResult(payment.getId(), pgStatus.transactionKey(),
                    PaymentStatus.PAID.name(), null);
            }
            case "FAILED" -> {
                payment.markFailed(pgStatus.reason());
                paymentRepository.save(payment);
                return new PaymentResult(payment.getId(), null,
                    PaymentStatus.FAILED.name(), pgStatus.reason());
            }
            default -> {
                return null; // 알 수 없는 상태 → 재시도
            }
        }
    }

    /**
     * 최종 Fallback: UNKNOWN 상태 저장 + "결제 확인 중" 응답.
     *
     * <p>모든 PG 실패 후 최종 안전장치.
     * UNKNOWN 상태 결제건은 배치/Outbox가 PG 상태를 확인하여 최종 전이한다.</p>
     *
     * @see <a href="05-payment-resilience.md §8.7">최종 Fallback</a>
     */
    private PaymentResult handleUnknownFallback(PaymentModel payment, Exception lastException) {
        log.error("모든 PG 결제 요청 실패 — UNKNOWN Fallback: paymentId={}, lastError={}",
            payment.getId(), lastException != null ? lastException.getMessage() : "unknown");

        payment.markUnknown();
        paymentRepository.save(payment);

        return new PaymentResult(payment.getId(), null,
            PaymentStatus.UNKNOWN.name(),
            "결제 확인 중입니다. 잠시 후 확인해주세요.");
    }

    private void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public PaymentModel getPayment(Long paymentId) {
        return paymentRepository.findById(paymentId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "결제 정보를 찾을 수 없습니다."));
    }

    public PaymentModel getPaymentByOrderId(Long orderId) {
        return paymentRepository.findByOrderId(orderId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "해당 주문의 결제 정보를 찾을 수 없습니다."));
    }

    public record PaymentResult(
        Long paymentId,
        String transactionKey,
        String status,
        String failureReason
    ) {}
}
