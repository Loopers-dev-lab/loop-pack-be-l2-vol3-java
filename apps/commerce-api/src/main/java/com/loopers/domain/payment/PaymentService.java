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

    private final PaymentRepository paymentRepository;
    private final PaymentClient paymentClient;

    public PaymentService(PaymentRepository paymentRepository, PaymentClient paymentClient) {
        this.paymentRepository = paymentRepository;
        this.paymentClient = paymentClient;
    }

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

    @Transactional(readOnly = true)
    public Payment getByOrderId(Long orderId) {
        return paymentRepository.findByOrderId(orderId)
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
     * 예외 전파 전략:
     * - PgClientException (4xx): catch → PaymentResult.failed() (CB 안 보임)
     * - PgTimeoutException: catch → query로 복구 시도 → 복구 실패 시 re-throw (CB record)
     * - PgServerException (500): catch 안 함 → Facade까지 전파 (CB record)
     */
    public PaymentResult requestPayment(Payment payment, PgApproveRequest request) {
        try {
            PgApproveResult approveResult = paymentClient.approve(request);

            if (approveResult.isPending()) {
                log.info("PG 결제 접수 완료 — 콜백 대기: orderId={}, txnKey={}",
                        payment.getOrderId(), approveResult.transactionKey());
                return PaymentResult.pending(approveResult.transactionKey());
            }

            return PaymentResult.failed("알 수 없는 PG 응답");

        } catch (PgClientException e) {
            log.info("PG 결제 거절 (4xx): orderId={}, reason={}", payment.getOrderId(), e.getMessage());
            return PaymentResult.failed(e.getMessage());

        } catch (PgTimeoutException e) {
            log.warn("PG 결제 타임아웃 — 조회 시도: orderId={}", payment.getOrderId());
            return handleTimeoutWithQuery(payment, request);
        }
        // PgServerException (500) — catch 안 함 → Facade까지 전파 → CB record
    }

    public PaymentResult verifyCallback(String transactionKey, Long userId) {
        PgQueryResult queryResult = paymentClient.query(transactionKey, userId);

        if (queryResult.isSuccess()) {
            log.info("PG 콜백 검증 성공: txnKey={}", transactionKey);
            return PaymentResult.approved(transactionKey);
        }
        if (queryResult.isFailed()) {
            log.info("PG 콜백 검증 — 결제 실패 확인: txnKey={}, reason={}", transactionKey, queryResult.reason());
            return PaymentResult.failed(queryResult.reason());
        }
        if (queryResult.isPending()) {
            log.warn("PG 콜백 검증 — 아직 PENDING: txnKey={}", transactionKey);
            return PaymentResult.pending(transactionKey);
        }

        log.warn("PG 콜백 검증 실패 — 상태 불확실: txnKey={}", transactionKey);
        return PaymentResult.unknown("콜백 검증 시 PG 상태 확인 실패");
    }

    public PgCancelResult cancelPgPayment(String transactionKey, Long userId) {
        log.info("PG 결제 취소 요청: txnKey={}", transactionKey);
        PgCancelResult result = paymentClient.cancel(transactionKey, userId);
        if (result.canceled()) {
            log.info("PG 결제 취소 성공: txnKey={}", transactionKey);
        } else {
            log.warn("PG 결제 취소 실패: txnKey={}, reason={}", transactionKey, result.reason());
        }
        return result;
    }

    /**
     * 타임아웃 시 조회 API로 결제 상태 확인
     *
     * - query 성공(SUCCESS/FAILED) → 결과 반환 (CB "성공"으로 봄)
     * - query NOT_FOUND → PG에 요청 미도달 → PgTimeoutException re-throw (CB record)
     * - query 자체 실패 → PG 진짜 죽음 → PgTimeoutException re-throw (CB record)
     */
    private PaymentResult handleTimeoutWithQuery(Payment payment, PgApproveRequest request) {
        PgQueryResult queryResult;
        try {
            queryResult = paymentClient.query(null, request.userId());
        } catch (PgException e) {
            throw new PgTimeoutException("결제 승인 타임아웃 + 조회도 실패", e);
        }

        if (queryResult.isSuccess()) {
            return PaymentResult.approved(queryResult.transactionKey());
        }
        if (queryResult.isFailed()) {
            return PaymentResult.failed(queryResult.reason());
        }
        if (queryResult.isNotFound()) {
            throw new PgTimeoutException("결제 승인 타임아웃 — PG에 요청 미도달");
        }

        return PaymentResult.unknown("타임아웃 후 상태 확인 — 아직 PENDING");
    }
}
