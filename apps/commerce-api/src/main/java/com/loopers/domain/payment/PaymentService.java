package com.loopers.domain.payment;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.PaymentErrorType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);
    private static final int PG_POLL_MAX_ATTEMPTS = 5;
    private static final long PG_POLL_INTERVAL_MS = 1500;

    private final PaymentRepository paymentRepository;
    private final PaymentClient paymentClient;

    public PaymentService(PaymentRepository paymentRepository, PaymentClient paymentClient) {
        this.paymentRepository = paymentRepository;
        this.paymentClient = paymentClient;
    }

    /** 결제 생성 — 멱등성 키 중복 시 CONFLICT (동일 요청 재처리 방지) */
    @Transactional
    public Payment create(Long orderId, int requestedAmount, String paymentMethod, String idempotencyKey) {
        paymentRepository.findByIdempotencyKey(idempotencyKey)
                .ifPresent(existing -> {
                    throw new CoreException(PaymentErrorType.DUPLICATE_IDEMPOTENCY_KEY);
                });
        Payment payment = Payment.request(orderId, requestedAmount, paymentMethod, idempotencyKey);
        return paymentRepository.save(payment);
    }

    @Transactional(readOnly = true)
    public Payment getById(Long paymentId) {
        return paymentRepository.findById(paymentId)
                .orElseThrow(() -> new CoreException(PaymentErrorType.PAYMENT_NOT_FOUND));
    }

    @Transactional
    public void approve(Long paymentId, String pgTxnId, int approvedAmount) {
        Payment payment = getById(paymentId);
        payment.approve(pgTxnId, approvedAmount);
        paymentRepository.save(payment);
    }

    @Transactional
    public void fail(Long paymentId) {
        Payment payment = getById(paymentId);
        payment.reject();
        paymentRepository.save(payment);
    }

    @Transactional
    public void markUnknown(Long paymentId) {
        Payment payment = getById(paymentId);
        payment.markUnknown();
        paymentRepository.save(payment);
    }

    /**
     * PG 결제 요청 + 결과 해석 (트랜잭션 없음 — 외부 HTTP 호출 포함)
     *
     * 1. PG에 결제 승인 요청 (POST)
     * 2. PENDING 응답 시 → 폴링으로 최종 결과 확인 (GET)
     * 3. 결과를 해석하여 PaymentResult 반환
     *
     * Facade는 이 결과만 보고 TX2(확정) 또는 보상을 결정한다.
     * PG 응답 코드 해석, 타임아웃 분기, 폴링 로직은 이 메서드 안에 캡슐화된다.
     */
    public PaymentResult requestPayment(Payment payment, PgApproveRequest request) {
        // 1. PG 결제 승인 요청
        PgApproveResult approveResult = paymentClient.approve(request);

        // 2. 응답별 분기
        if (approveResult.isRejected()) {
            log.info("PG 결제 거절: orderId={}, reason={}", payment.getOrderId(), approveResult.reason());
            return PaymentResult.failed(approveResult.reason());
        }

        if (approveResult.isTimeout()) {
            log.warn("PG 결제 타임아웃: orderId={}", payment.getOrderId());
            return handleTimeoutWithQuery(payment, request);
        }

        if (approveResult.isError()) {
            log.warn("PG 서버 에러: orderId={}, reason={}", payment.getOrderId(), approveResult.reason());
            return PaymentResult.failed(approveResult.reason());
        }

        // 3. PENDING — 비동기 결과 폴링
        if (approveResult.isPending()) {
            return pollForResult(payment, approveResult.transactionKey(), request.userId());
        }

        return PaymentResult.failed("알 수 없는 PG 응답");
    }

    /**
     * PG PENDING 상태에서 폴링으로 최종 결과 확인
     */
    private PaymentResult pollForResult(Payment payment, String transactionKey, Long userId) {
        for (int attempt = 1; attempt <= PG_POLL_MAX_ATTEMPTS; attempt++) {
            try {
                Thread.sleep(PG_POLL_INTERVAL_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return PaymentResult.unknown("폴링 중 인터럽트 발생");
            }

            PgQueryResult queryResult = paymentClient.query(transactionKey, userId);

            if (queryResult.isSuccess()) {
                log.info("PG 결제 승인 확인: orderId={}, txnKey={}", payment.getOrderId(), transactionKey);
                return PaymentResult.approved(transactionKey);
            }

            if (queryResult.isFailed()) {
                log.info("PG 결제 거절 확인: orderId={}, reason={}", payment.getOrderId(), queryResult.reason());
                return PaymentResult.failed(queryResult.reason());
            }

            log.debug("PG 상태 조회 attempt {}/{}: 아직 PENDING (orderId={})",
                    attempt, PG_POLL_MAX_ATTEMPTS, payment.getOrderId());
        }

        log.warn("PG 폴링 한도 초과: orderId={}, txnKey={} — UNKNOWN 처리", payment.getOrderId(), transactionKey);
        return PaymentResult.unknown("PG 결과 확인 한도 초과 — 대사 배치에서 확인 예정");
    }

    /**
     * 타임아웃 시 조회 API로 결제 상태 확인
     */
    private PaymentResult handleTimeoutWithQuery(Payment payment, PgApproveRequest request) {
        PgQueryResult queryResult = paymentClient.query(
                null, request.userId());

        if (queryResult.isSuccess()) {
            return PaymentResult.approved(queryResult.transactionKey());
        }
        if (queryResult.isFailed()) {
            return PaymentResult.failed(queryResult.reason());
        }
        if (queryResult.isNotFound()) {
            return PaymentResult.failed("PG에 결제 요청이 도달하지 않음");
        }

        return PaymentResult.unknown("타임아웃 후 상태 확인 실패");
    }
}
