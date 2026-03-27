package com.loopers.application.payment;

import com.loopers.domain.common.Money;
import com.loopers.domain.order.Order;
import com.loopers.domain.order.OrderRepository;
import com.loopers.domain.payment.Payment;
import com.loopers.domain.payment.PaymentRepository;
import com.loopers.infrastructure.pg.PgClient;
import com.loopers.infrastructure.pg.dto.PgPaymentRequest;
import com.loopers.infrastructure.pg.dto.PgPaymentResponse;
import com.loopers.infrastructure.pg.exception.PgClientException;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.concurrent.TimeoutException;

/**
 * 결제 서비스.
 */
@Service
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);
    private static final String CALLBACK_URL = "http://localhost:8080/api/v1/payments/callback";
    private static final String CIRCUIT_BREAKER_NAME = "pgPayment";

    private final PaymentRepository paymentRepository;
    private final OrderRepository orderRepository;
    private final PgClient pgClient;

    public PaymentService(PaymentRepository paymentRepository,
                          OrderRepository orderRepository,
                          PgClient pgClient) {
        this.paymentRepository = paymentRepository;
        this.orderRepository = orderRepository;
        this.pgClient = pgClient;
    }

    /**
     * 결제 요청.
     *
     * @param userId 사용자 ID
     * @param orderId 주문 ID
     * @param cardType 카드 타입
     * @param cardNo 카드 번호
     * @return 결제 결과
     */
    @Transactional
    @CircuitBreaker(name = CIRCUIT_BREAKER_NAME, fallbackMethod = "requestPaymentFallback")
    public PaymentResult requestPayment(Long userId, Long orderId, String cardType, String cardNo) {
        // 1. 주문 조회
        Order order = orderRepository.findByIdAndUserId(orderId, userId)
                .orElseThrow(() -> new CoreException(ErrorType.ORDER_NOT_FOUND));

        // 2. 이미 결제가 존재하는지 확인
        paymentRepository.findByOrderId(orderId)
                .ifPresent(existing -> {
                    throw new CoreException(ErrorType.PAYMENT_ALREADY_EXISTS);
                });

        // 3. PG 결제 요청
        PgPaymentResponse pgResponse;
        try {
            PgPaymentRequest pgRequest = new PgPaymentRequest(
                    String.valueOf(orderId),
                    cardType,
                    cardNo,
                    order.getTotalPrice().amount(),
                    CALLBACK_URL
            );
            pgResponse = pgClient.requestPayment(userId, pgRequest);
        } catch (PgClientException e) {
            log.error("PG 결제 요청 실패 - orderId: {}, error: {}", orderId, e.getMessage(), e);
            throw e; // CircuitBreaker가 감지하도록 다시 던짐
        }

        // 4. 결제 정보 저장 (PENDING)
        Payment payment = Payment.create(
                orderId,
                pgResponse.transactionId(),
                new Money(pgResponse.amount())
        );
        Payment savedPayment = paymentRepository.save(payment);

        return PaymentResult.from(savedPayment);
    }

    /**
     * CircuitBreaker OPEN 시 fallback.
     */
    @Transactional
    public PaymentResult requestPaymentFallback(Long userId, Long orderId, String cardType, String cardNo,
                                                 CallNotPermittedException e) {
        log.warn("CircuitBreaker OPEN - 결제 요청 차단. orderId: {}, userId: {}", orderId, userId, e);

        String errorMessage = "현재 결제 시스템이 불안정합니다. 잠시 후 다시 시도해주세요.";
        return savePendingRetryPayment(orderId, userId, errorMessage);
    }

    /**
     * Timeout 발생 시 fallback.
     */
    @Transactional
    public PaymentResult requestPaymentFallback(Long userId, Long orderId, String cardType, String cardNo,
                                                 TimeoutException e) {
        log.warn("Timeout 발생 - 결제 요청 시간 초과. orderId: {}, userId: {}", orderId, userId, e);

        String errorMessage = "결제 요청 처리 중 시간이 초과되었습니다.";
        // Timeout 시에는 PG에 요청이 갔을 수도 있으므로 콜백을 기다려야 함
        return savePendingRetryPayment(orderId, userId, errorMessage);
    }

    /**
     * PG 통신 예외 시 fallback.
     */
    @Transactional
    public PaymentResult requestPaymentFallback(Long userId, Long orderId, String cardType, String cardNo,
                                                 PgClientException e) {
        log.error("PG 통신 예외 발생. orderId: {}, userId: {}, errorCode: {}, retryable: {}",
                orderId, userId, e.getErrorCode(), e.isRetryable(), e);

        String errorMessage = "결제 처리 중 오류가 발생했습니다.";
        return savePendingRetryPayment(orderId, userId, errorMessage);
    }

    /**
     * 기타 예외 시 fallback.
     */
    @Transactional
    public PaymentResult requestPaymentFallback(Long userId, Long orderId, String cardType, String cardNo,
                                                 Throwable e) {
        // CoreException은 fallback 처리하지 않고 그대로 전파
        if (e instanceof CoreException) {
            throw (CoreException) e;
        }

        log.error("예상치 못한 예외 발생. orderId: {}, userId: {}, exception: {}",
                orderId, userId, e.getClass().getSimpleName(), e);

        String errorMessage = "결제 처리 중 오류가 발생했습니다.";
        return savePendingRetryPayment(orderId, userId, errorMessage);
    }

    /**
     * PENDING_RETRY 상태로 결제 저장.
     */
    private PaymentResult savePendingRetryPayment(Long orderId, Long userId, String errorMessage) {
        // 주문 조회 (금액 확인용)
        Order order = orderRepository.findByIdAndUserId(orderId, userId).orElse(null);
        if (order == null) {
            return PaymentResult.error(orderId, errorMessage);
        }

        // 이미 결제가 있는지 확인
        Payment existingPayment = paymentRepository.findByOrderId(orderId).orElse(null);
        if (existingPayment != null) {
            // 이미 결제가 있으면 상태만 업데이트
            existingPayment.markAsPendingRetry(errorMessage);
            Payment saved = paymentRepository.save(existingPayment);
            return PaymentResult.fallback(saved, errorMessage);
        }

        // 새로 PENDING_RETRY 상태로 저장
        Payment payment = Payment.createPendingRetry(orderId, order.getTotalPrice(), errorMessage);
        Payment saved = paymentRepository.save(payment);
        return PaymentResult.fallback(saved, errorMessage);
    }

    /**
     * 콜백 처리.
     *
     * @param callbackData PG 콜백 데이터
     * @return 결제 결과
     */
    @Transactional
    public PaymentResult handleCallback(PgPaymentResponse callbackData) {
        // 1. 결제 정보 조회
        Payment payment = paymentRepository.findByTransactionId(callbackData.transactionId())
                .orElseThrow(() -> new CoreException(ErrorType.PAYMENT_NOT_FOUND));

        // 2. 결제 상태 업데이트
        if (callbackData.isSuccess()) {
            payment.markAsSuccess();
        } else if (callbackData.isFailed()) {
            payment.markAsFail(callbackData.failureReason());
        }
        // PENDING인 경우 상태 변경 없음

        // 3. 저장
        Payment savedPayment = paymentRepository.save(payment);

        return PaymentResult.from(savedPayment);
    }

    /**
     * 결제 조회.
     *
     * @param paymentId 결제 ID
     * @return 결제 결과
     */
    @Transactional(readOnly = true)
    public PaymentResult getPayment(Long paymentId) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new CoreException(ErrorType.PAYMENT_NOT_FOUND));
        return PaymentResult.from(payment);
    }

    /**
     * 주문으로 결제 조회.
     *
     * @param orderId 주문 ID
     * @return 결제 결과
     */
    @Transactional(readOnly = true)
    public PaymentResult getPaymentByOrderId(Long orderId) {
        Payment payment = paymentRepository.findByOrderId(orderId)
                .orElseThrow(() -> new CoreException(ErrorType.PAYMENT_NOT_FOUND));
        return PaymentResult.from(payment);
    }

    /**
     * 단건 결제 상태 동기화.
     * PG에 결제 상태를 조회하여 로컬 DB와 동기화.
     *
     * @param userId 사용자 ID
     * @param paymentId 결제 ID
     * @return 동기화된 결제 결과
     */
    @Transactional
    public PaymentResult syncPaymentStatus(Long userId, Long paymentId) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new CoreException(ErrorType.PAYMENT_NOT_FOUND));

        if (!payment.needsSync()) {
            log.info("동기화 불필요 - paymentId: {}, status: {}", paymentId, payment.getStatus());
            return PaymentResult.from(payment);
        }

        return syncPaymentWithPg(userId, payment);
    }

    /**
     * 주문으로 결제 상태 동기화.
     *
     * @param userId 사용자 ID
     * @param orderId 주문 ID
     * @return 동기화된 결제 결과
     */
    @Transactional
    public PaymentResult syncPaymentStatusByOrderId(Long userId, Long orderId) {
        Payment payment = paymentRepository.findByOrderId(orderId)
                .orElseThrow(() -> new CoreException(ErrorType.PAYMENT_NOT_FOUND));

        if (!payment.needsSync()) {
            log.info("동기화 불필요 - orderId: {}, status: {}", orderId, payment.getStatus());
            return PaymentResult.from(payment);
        }

        return syncPaymentWithPg(userId, payment);
    }

    /**
     * 모든 PENDING 상태 결제 동기화.
     * 배치 또는 스케줄러에서 호출.
     *
     * @return 동기화 결과 목록
     */
    @Transactional
    public PaymentSyncResult syncAllPendingPayments() {
        var pendingPayments = paymentRepository.findAllPendingPayments();
        log.info("동기화 대상 결제 건수: {}", pendingPayments.size());

        int successCount = 0;
        int failCount = 0;
        int skipCount = 0;

        for (Payment payment : pendingPayments) {
            try {
                // transactionId가 없으면 orderId로 조회
                if (payment.getTransactionId() == null) {
                    syncPaymentByOrderId(payment);
                } else {
                    syncPaymentByTransactionId(payment);
                }
                successCount++;
            } catch (PgClientException e) {
                log.warn("PG 조회 실패 - paymentId: {}, error: {}", payment.getId(), e.getMessage());
                failCount++;
            } catch (Exception e) {
                log.error("동기화 중 예외 발생 - paymentId: {}", payment.getId(), e);
                failCount++;
            }
        }

        log.info("동기화 완료 - 성공: {}, 실패: {}, 스킵: {}", successCount, failCount, skipCount);
        return new PaymentSyncResult(pendingPayments.size(), successCount, failCount, skipCount);
    }

    /**
     * PG에 결제 상태 조회하여 동기화.
     */
    private PaymentResult syncPaymentWithPg(Long userId, Payment payment) {
        PgPaymentResponse pgResponse = null;

        try {
            // transactionId가 있으면 transactionId로 조회
            if (payment.getTransactionId() != null) {
                pgResponse = pgClient.getPayment(userId, payment.getTransactionId())
                        .orElse(null);
            }

            // transactionId로 조회 실패 시 orderId로 조회
            if (pgResponse == null) {
                pgResponse = pgClient.getPaymentByOrderId(userId, String.valueOf(payment.getOrderId()))
                        .orElse(null);
            }

            if (pgResponse == null) {
                log.warn("PG에서 결제 정보를 찾을 수 없음 - paymentId: {}, orderId: {}",
                        payment.getId(), payment.getOrderId());
                return PaymentResult.from(payment);
            }

            // 상태 동기화
            payment.syncWithPgResponse(
                    pgResponse.transactionId(),
                    pgResponse.status().name(),
                    pgResponse.failureReason()
            );
            Payment saved = paymentRepository.save(payment);

            log.info("결제 상태 동기화 완료 - paymentId: {}, newStatus: {}",
                    payment.getId(), saved.getStatus());
            return PaymentResult.from(saved);

        } catch (PgClientException e) {
            log.error("PG 상태 조회 실패 - paymentId: {}, error: {}", payment.getId(), e.getMessage(), e);
            throw e;
        }
    }

    /**
     * transactionId로 PG 조회하여 동기화.
     */
    private void syncPaymentByTransactionId(Payment payment) {
        // userId를 모르므로 orderId에서 userId를 가져옴
        var order = orderRepository.findById(payment.getOrderId()).orElse(null);
        if (order == null) {
            log.warn("주문 정보 없음 - paymentId: {}, orderId: {}", payment.getId(), payment.getOrderId());
            return;
        }

        var pgResponse = pgClient.getPayment(order.getUserId(), payment.getTransactionId())
                .orElse(null);

        if (pgResponse != null) {
            payment.syncWithPgResponse(
                    pgResponse.transactionId(),
                    pgResponse.status().name(),
                    pgResponse.failureReason()
            );
            paymentRepository.save(payment);
            log.info("결제 동기화 완료 - paymentId: {}, status: {}", payment.getId(), payment.getStatus());
        }
    }

    /**
     * orderId로 PG 조회하여 동기화.
     */
    private void syncPaymentByOrderId(Payment payment) {
        var order = orderRepository.findById(payment.getOrderId()).orElse(null);
        if (order == null) {
            log.warn("주문 정보 없음 - paymentId: {}, orderId: {}", payment.getId(), payment.getOrderId());
            return;
        }

        var pgResponse = pgClient.getPaymentByOrderId(order.getUserId(), String.valueOf(payment.getOrderId()))
                .orElse(null);

        if (pgResponse != null) {
            payment.syncWithPgResponse(
                    pgResponse.transactionId(),
                    pgResponse.status().name(),
                    pgResponse.failureReason()
            );
            paymentRepository.save(payment);
            log.info("결제 동기화 완료 - paymentId: {}, status: {}", payment.getId(), payment.getStatus());
        }
    }
}
