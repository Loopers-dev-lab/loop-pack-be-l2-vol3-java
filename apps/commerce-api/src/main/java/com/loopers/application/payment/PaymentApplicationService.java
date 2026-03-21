package com.loopers.application.payment;

import com.loopers.domain.payment.CardType;
import com.loopers.domain.payment.Payment;
import com.loopers.domain.payment.PaymentDomainService;
import com.loopers.domain.payment.PaymentGateway;
import com.loopers.domain.payment.PaymentGateway.TransactionResult;
import com.loopers.domain.payment.PaymentGatewayException;
import com.loopers.domain.payment.PaymentGatewayRetryableException;
import com.loopers.domain.payment.PaymentStatus;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@RequiredArgsConstructor
@Component
public class PaymentApplicationService {

    private final PaymentTransactionHelper transactionHelper;
    private final PaymentDomainService paymentDomainService;
    private final PaymentGateway paymentGateway;

    /**
     * 결제 요청.
     * TX1: Payment(PENDING) + Order(PAYMENT_PENDING) 저장 → 커밋
     * 외부: PG 호출 (타임아웃/실패 가능, 재시도 없음)
     * TX2: Payment(IN_PROGRESS) 업데이트 → 커밋
     *
     * PG 호출 실패 시 Payment는 PENDING 상태로 남음.
     * POST /payments/orders/{orderId}/sync로 orderId 기반 PG 조회를 통해 복구 시도 가능.
     */
    public Payment requestPayment(Long userId, Long orderId, CardType cardType, String cardNo) {
        // TX1: PENDING 상태로 저장
        Payment payment = transactionHelper.initializePayment(orderId, userId, cardType, cardNo);

        // 외부 호출: PG 결제 요청 (트랜잭션 밖, @Retry 미적용 — PG 멱등성 미보장)
        String transactionKey;
        try {
            transactionKey = paymentGateway.requestPayment(
                userId, orderId, cardType, cardNo, payment.getAmount()
            );
        } catch (CallNotPermittedException e) {
            log.warn("[서킷 브레이커 Open] orderId={}, PENDING 상태로 응답합니다.", orderId);
            return payment;
        } catch (PaymentGatewayRetryableException e) {
            log.warn("[PG 일시적 장애] orderId={}, error={}. PENDING 상태로 응답합니다.", orderId, e.getMessage());
            return payment;
        } catch (PaymentGatewayException e) {
            log.warn("[결제 요청 거절] orderId={}, userId={}, error={}. PENDING 결제를 즉시 실패 처리합니다.",
                orderId, userId, e.getMessage());
            transactionHelper.cancelPendingPayment(orderId, userId, "PG 요청 거절: " + e.getMessage());
            throw new CoreException(ErrorType.INTERNAL_ERROR,
                "PG 요청에 실패했습니다: " + e.getMessage());
        }

        // TX2: IN_PROGRESS로 전환
        return transactionHelper.markPaymentInProgress(orderId, userId, transactionKey);
    }

    /**
     * PG 콜백 수신.
     */
    public void handleCallback(String transactionKey, String status, String reason) {
        log.info("[PG 콜백 수신] transactionKey={}, status={}, reason={}", transactionKey, status, reason);
        transactionHelper.applyPaymentResult(transactionKey, status, reason);
    }

    /**
     * transactionKey 기반 상태 동기화.
     * IN_PROGRESS 상태의 Payment에 대해 PG 조회 후 상태를 반영한다.
     */
    public Payment syncPaymentStatus(Long userId, String transactionKey) {
        Payment payment = paymentDomainService.getByTransactionKey(transactionKey);

        if (!payment.getUserId().equals(userId)) {
            throw new CoreException(ErrorType.NOT_FOUND, "결제 정보를 찾을 수 없습니다.");
        }

        if (payment.getStatus() == PaymentStatus.PAID || payment.getStatus() == PaymentStatus.FAILED) {
            return payment;
        }

        if (payment.getStatus() == PaymentStatus.PENDING) {
            throw new CoreException(ErrorType.BAD_REQUEST,
                "PG에 아직 접수되지 않은 결제입니다. orderId 기반 동기화를 사용해주세요.");
        }

        TransactionResult result;
        try {
            result = paymentGateway.getTransactionStatus(userId, transactionKey);
        } catch (PaymentGatewayException e) {
            log.warn("[PG 상태 조회 실패] transactionKey={}, error={}", transactionKey, e.getMessage());
            throw new CoreException(ErrorType.INTERNAL_ERROR,
                "PG 상태 조회에 실패했습니다: " + e.getMessage());
        }

        if (result.isPending()) {
            return payment;
        }

        return transactionHelper.applyPaymentResult(transactionKey, result.status(), result.reason());
    }

    /**
     * orderId 기반 PENDING Payment 복구.
     * transactionKey가 없는(PG 요청 실패/유실) 결제를 orderId로 PG에 조회하여 복구한다.
     *
     * 내부 PENDING 건수:
     *   0건 → BAD_REQUEST
     *   1건 → PG 조회 후 복구 시도
     *   2건 이상 → 정합성 사고, 자동 처리 거부
     *
     * PG 결과 건수:
     *   0건 → PG 미접수, PENDING 유지
     *   1건 → 정상 복구
     *   2건 이상 → 이중 결제 사고, 자동 처리 거부
     */
    public Payment syncByOrderId(Long userId, Long orderId) {
        // 1. 사전 필터: PENDING Payment 존재 여부 확인 (성능 최적화용, 정합성 판단은 recoverPendingPayment 내부에서)
        boolean hasPending = paymentDomainService.getByOrderId(orderId).stream()
            .anyMatch(p -> p.getUserId().equals(userId) && p.getStatus() == PaymentStatus.PENDING);

        if (!hasPending) {
            throw new CoreException(ErrorType.BAD_REQUEST, "PENDING 상태의 결제가 없습니다.");
        }

        // 2. PG에 orderId 기반 조회
        List<TransactionResult> pgResults;
        try {
            pgResults = paymentGateway.getTransactionsByOrderId(userId, orderId);
        } catch (PaymentGatewayException e) {
            log.warn("[PG orderId 조회 실패] orderId={}, error={}", orderId, e.getMessage());
            throw new CoreException(ErrorType.INTERNAL_ERROR,
                "PG 상태 조회에 실패했습니다: " + e.getMessage());
        }

        // 3. PG 결과 건수별 분기
        if (pgResults.isEmpty()) {
            // PG 정상 응답이지만 결제 0건 → PG 미접수 확정 → 실패 처리 + 재고/쿠폰 복원
            log.info("[PG 미접수 확정] orderId={}, PENDING 결제를 실패 처리합니다.", orderId);
            return transactionHelper.cancelPendingPayment(orderId, userId, "PG 미접수 확인됨");
        }

        if (pgResults.size() > 1) {
            log.error("[이중 결제 사고] orderId={}, PG 결제 {}건 존재. 자동 처리 불가.",
                orderId, pgResults.size());
            throw new CoreException(ErrorType.INTERNAL_ERROR,
                "결제 상태 확인이 필요합니다. 고객센터에 문의해주세요.");
        }

        // 4. 정합성 판단과 복구는 잠금 조회 기준으로 recoverPendingPayment 내부에서 수행
        TransactionResult pgResult = pgResults.get(0);
        return transactionHelper.recoverPendingPayment(
            orderId, userId, pgResult.transactionKey(), pgResult.status(), pgResult.reason()
        );
    }
}
