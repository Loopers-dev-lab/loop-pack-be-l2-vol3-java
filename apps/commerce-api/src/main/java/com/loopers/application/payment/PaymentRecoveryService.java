package com.loopers.application.payment;

import com.loopers.domain.payment.Payment;
import com.loopers.domain.payment.PaymentRepository;
import com.loopers.domain.payment.PaymentStatus;
import com.loopers.domain.payment.PgClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Slf4j
@RequiredArgsConstructor
@Component
public class PaymentRecoveryService {

    private final PaymentRepository paymentRepository;
    private final PgClient pgClient;

    // PENDING 결제 복구 전략:
    //
    // 두 가지 케이스로 나뉜다.
    //
    // 케이스 1. pgTransactionKey가 있는 경우 (콜백 미수신)
    //   → PG는 결제를 받아서 transactionKey를 발급했지만, 콜백이 우리 서버에 도달하지 않은 상황
    //   → transactionKey로 PG 상태 직접 조회 → 상태 반영
    //
    // 케이스 2. pgTransactionKey가 없는 경우 (요청 타임아웃)
    //   → PG 호출 자체가 타임아웃으로 실패. PG가 처리했는지조차 불확실
    //   → orderId로 PG에 결제 기록 조회 → 있으면 SUCCESS/FAILED 반영, 없으면 FAILED 처리
    //
    // 복구 주기: 1분 (fixedDelay = 이전 실행 완료 후 1분)
    // fixedRate가 아닌 fixedDelay를 선택한 이유:
    //   - fixedRate는 이전 실행이 길어지면 다음 실행이 겹칠 수 있다
    //   - 결제 복구는 정확성이 중요하므로 이전 실행이 끝난 후 간격을 두는 fixedDelay 선택
    @Transactional
    @Scheduled(fixedDelay = 60_000)
    public void recoverPendingPayments() {
        List<Payment> pendingPayments = paymentRepository.findByStatus(PaymentStatus.PENDING);
        if (pendingPayments.isEmpty()) return;

        log.info("[Recovery] PENDING 결제 복구 시작. count={}", pendingPayments.size());

        for (Payment payment : pendingPayments) {
            try {
                recoverPayment(payment);
            } catch (Exception e) {
                // PG가 현재 다운된 경우 등 → 이번 실행은 skip, 다음 주기에 재시도
                log.warn("[Recovery] 결제 복구 실패 - 다음 주기에 재시도. paymentId={}, error={}",
                    payment.getId(), e.getMessage());
            }
        }
    }

    private void recoverPayment(Payment payment) {
        Optional<PgClient.PgPaymentResponse> pgResponse;

        if (payment.getPgTransactionKey() != null) {
            pgResponse = pgClient.getPaymentByTransactionKey(payment.getPgTransactionKey());
        } else {
            pgResponse = pgClient.getPaymentByOrderId(String.valueOf(payment.getOrderId()));
        }

        if (pgResponse.isEmpty()) {
            // PG에 결제 기록 없음 = 타임아웃 발생 전에 PG가 처리하지 못한 것
            payment.markFailed("PG 결제 미처리 - 복구 시 확인 불가");
            log.info("[Recovery] FAILED 처리. paymentId={}", payment.getId());
            return;
        }

        PgClient.PgPaymentResponse response = pgResponse.get();

        if (payment.getPgTransactionKey() == null) {
            payment.assignPgTransactionKey(response.transactionKey());
        }

        if ("SUCCESS".equals(response.status())) {
            payment.markSuccess();
            log.info("[Recovery] SUCCESS 처리. paymentId={}", payment.getId());
        } else {
            payment.markFailed(response.reason());
            log.info("[Recovery] FAILED 처리. paymentId={}, reason={}", payment.getId(), response.reason());
        }
    }
}
