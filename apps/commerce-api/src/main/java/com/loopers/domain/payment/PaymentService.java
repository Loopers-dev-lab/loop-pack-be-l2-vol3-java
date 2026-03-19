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
     * 응답별 분기:
     * - 4xx (REJECTED): 요청 자체가 잘못됨 → 즉시 FAILED
     * - 500 (ERROR): PG 서버 에러 → 즉시 FAILED (재시도하지 않음 — 같은 에러 반복 가능성 높음)
     * - 타임아웃 (TIMEOUT): 결제 여부 불확실 → 조회 API로 한 번 확인
     * - PENDING: PG 접수 완료 → 콜백 대기
     *
     * 500 에러를 재시도하지 않는 근거 (케브 멘토):
     * "500은 다시 호출해도 500이 발생될 가능성이 높다. 타임아웃은 일시적인 상황이 많은데
     *  500이 발생하면 두 번째도 500이 더 크다. 이런 경우 retry를 시도하지 않는다."
     */
    public PaymentResult requestPayment(Payment payment, PgApproveRequest request) {
        // 1. PG 결제 승인 요청
        PgApproveResult approveResult = paymentClient.approve(request);

        // 2. 응답별 분기
        if (approveResult.isRejected()) {
            log.info("PG 결제 거절 (4xx): orderId={}, reason={}", payment.getOrderId(), approveResult.reason());
            return PaymentResult.failed(approveResult.reason());
        }

        if (approveResult.isError()) {
            log.warn("PG 서버 에러 (500): orderId={}, reason={}", payment.getOrderId(), approveResult.reason());
            return PaymentResult.failed(approveResult.reason());
        }

        if (approveResult.isTimeout()) {
            log.warn("PG 결제 타임아웃: orderId={}", payment.getOrderId());
            return handleTimeoutWithQuery(payment, request);
        }

        // 3. PENDING — PG가 접수함, 결과는 콜백으로 수신
        if (approveResult.isPending()) {
            log.info("PG 결제 접수 완료 — 콜백 대기: orderId={}, txnKey={}",
                    payment.getOrderId(), approveResult.transactionKey());
            return PaymentResult.pending(approveResult.transactionKey());
        }

        return PaymentResult.failed("알 수 없는 PG 응답");
    }

    /**
     * PG 콜백 검증: PG 조회 API로 실제 상태를 확인한다.
     *
     * 콜백은 누구든 POST 요청을 보낼 수 있으므로, 콜백 데이터를 그대로 신뢰하지 않는다.
     * PG 조회 API로 transactionKey의 실제 상태를 확인한 뒤 PaymentResult를 반환한다.
     */
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

    /**
     * PG 결제 취소 — 뒤늦은 PG 성공 시 대사 배치에서 호출
     *
     * PG cancel() API를 호출하여 승인된 결제를 취소한다.
     * cancel() 실패 시 다음 배치에서 재시도한다.
     */
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
     * 타임아웃은 "모르는 상태"이므로, 즉시 재시도보다 조회 API로 결과 확인이 우선이다.
     * 조회 결과에 따라 성공/실패/UNKNOWN을 반환한다.
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
