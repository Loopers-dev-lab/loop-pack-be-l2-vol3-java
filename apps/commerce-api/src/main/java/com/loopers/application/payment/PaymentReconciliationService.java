package com.loopers.application.payment;

import com.loopers.domain.order.Order;
import com.loopers.domain.order.OrderService;
import com.loopers.domain.payment.Payment;
import com.loopers.domain.payment.PaymentRepository;
import com.loopers.domain.payment.PaymentResult;
import com.loopers.domain.payment.PaymentService;
import com.loopers.domain.payment.PaymentStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.ZonedDateTime;
import java.util.List;

/**
 * 대사 배치 — 10분 주기로 결제 상태 불일치를 보정한다.
 *
 * 3가지 역할:
 * 1. REQUESTED/UNKNOWN 상태가 10분 이상 → PG 조회 → 결과 반영
 * 2. FAILED인데 PG가 SUCCESS → cancel() 호출 (뒤늦은 PG 성공)
 * 3. DLQ 재처리 (보상 실패 건 재시도)
 */
@Component
public class PaymentReconciliationService {

    private static final Logger log = LoggerFactory.getLogger(PaymentReconciliationService.class);
    private static final int STALE_THRESHOLD_MINUTES = 10;

    private final PaymentRepository paymentRepository;
    private final PaymentService paymentService;
    private final PaymentFacade paymentFacade;
    private final OrderService orderService;

    public PaymentReconciliationService(PaymentRepository paymentRepository,
                                         PaymentService paymentService,
                                         PaymentFacade paymentFacade,
                                         OrderService orderService) {
        this.paymentRepository = paymentRepository;
        this.paymentService = paymentService;
        this.paymentFacade = paymentFacade;
        this.orderService = orderService;
    }

    /**
     * 10분 주기 대사 배치
     */
    @Scheduled(fixedDelay = 600_000, initialDelay = 60_000)
    public void reconcile() {
        log.info("대사 배치 시작");

        int stalePending = reconcileStalePayments(PaymentStatus.REQUESTED);
        int staleUnknown = reconcileStalePayments(PaymentStatus.UNKNOWN);
        int lateSuccess = reconcileLatePgSuccess();
        int dlqRetried = retryDlq();

        log.info("대사 배치 완료 — REQUESTED 보정: {}, UNKNOWN 보정: {}, 뒤늦은 PG 성공 취소: {}, DLQ 재처리: {}",
                stalePending, staleUnknown, lateSuccess, dlqRetried);
    }

    /**
     * 역할 1: REQUESTED/UNKNOWN 상태가 10분 이상 지속된 Payment를 PG 조회 → 결과 반영
     *
     * - PG SUCCESS → confirmPayment() (TX2 실행)
     * - PG FAILED → compensatePayment() (보상 실행)
     * - PG PENDING 또는 조회 실패 → 다음 배치에서 재시도 (상태 유지)
     */
    private int reconcileStalePayments(PaymentStatus targetStatus) {
        ZonedDateTime threshold = ZonedDateTime.now().minusMinutes(STALE_THRESHOLD_MINUTES);
        List<Payment> stalePayments = paymentRepository.findAllByStatusAndRequestedBefore(targetStatus, threshold);

        int processed = 0;
        for (Payment payment : stalePayments) {
            try {
                Order order = orderService.getById(payment.getOrderId());
                PaymentResult result = paymentService.verifyCallback(
                        payment.getPgTxnId(), order.getUserId());

                if (result.isApproved()) {
                    paymentFacade.confirmPayment(payment.getOrderId(), result.transactionKey());
                    log.info("대사 배치 — {} → APPROVED: orderId={}", targetStatus, payment.getOrderId());
                    processed++;
                } else if (result.isFailed()) {
                    paymentFacade.compensatePayment(payment.getOrderId());
                    log.info("대사 배치 — {} → FAILED + 보상: orderId={}", targetStatus, payment.getOrderId());
                    processed++;
                } else {
                    log.debug("대사 배치 — {} 아직 미확정, 다음 배치에서 재시도: orderId={}",
                            targetStatus, payment.getOrderId());
                }
            } catch (Exception e) {
                log.error("대사 배치 — {} 처리 실패: orderId={}", targetStatus, payment.getOrderId(), e);
            }
        }
        return processed;
    }

    /**
     * 역할 2: 뒤늦은 PG 성공 → cancel() 호출
     *
     * 우리는 FAILED 처리 + 보상 완료했는데, PG에서는 실제로 승인된 경우.
     * PG cancel()을 호출해서 돈을 돌려준다.
     *
     * 대상: FAILED 상태이고, failedAt이 10분 이상 지난 건 (보상이 완전히 끝난 후)
     */
    private int reconcileLatePgSuccess() {
        ZonedDateTime threshold = ZonedDateTime.now().minusMinutes(STALE_THRESHOLD_MINUTES);
        List<Payment> failedPayments = paymentRepository.findAllByStatusAndFailedBefore(
                PaymentStatus.FAILED, threshold);

        int canceled = 0;
        for (Payment payment : failedPayments) {
            if (payment.getPgTxnId() == null) {
                // PG에 접수조차 안 된 건 — 조회 불필요
                continue;
            }

            try {
                Order order = orderService.getById(payment.getOrderId());

                // PG 조회 API로 실제 상태 확인 (PaymentService 캡슐화)
                PaymentResult pgResult = paymentService.verifyCallback(payment.getPgTxnId(), order.getUserId());

                if (pgResult.isApproved()) {
                    // PG는 SUCCESS인데 우리는 FAILED → PG 취소 호출
                    log.warn("뒤늦은 PG 성공 감지 — cancel() 호출: orderId={}, txnKey={}",
                            payment.getOrderId(), payment.getPgTxnId());
                    paymentService.cancelPgPayment(payment.getPgTxnId(), order.getUserId());
                    canceled++;
                }
                // PG도 FAILED면 정상 — 아무것도 안 함
            } catch (Exception e) {
                log.error("뒤늦은 PG 성공 처리 실패 — 다음 배치에서 재시도: orderId={}",
                        payment.getOrderId(), e);
            }
        }
        return canceled;
    }

    /**
     * 역할 3: DLQ 재처리
     */
    private int retryDlq() {
        paymentFacade.retryPendingCompensations();
        return 0; // retryPendingCompensations() 내부에서 로깅
    }
}
