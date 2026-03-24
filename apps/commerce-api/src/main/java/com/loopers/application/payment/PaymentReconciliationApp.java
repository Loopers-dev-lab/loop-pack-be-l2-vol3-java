package com.loopers.application.payment;

import com.loopers.application.payment.pg.PgClient;
import com.loopers.application.payment.pg.PgPaymentDto;
import com.loopers.domain.payment.Payment;
import com.loopers.domain.payment.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

@Slf4j
@RequiredArgsConstructor
@Component
public class PaymentReconciliationApp {

    private final PaymentRepository paymentRepository;
    private final PgClient pgClient;
    private final PaymentApp paymentApp;

    public void reconcile(ZonedDateTime threshold) {
        List<Payment> stalePayments = paymentRepository.findPendingPaymentsOlderThan(threshold);
        for (Payment payment : stalePayments) {
            try {
                reconcilePayment(payment);
            } catch (Exception e) {
                log.error("Reconciliation 실패 - orderId: {}, error: {}", payment.orderId(), e.getMessage());
            }
        }
    }

    private void reconcilePayment(Payment payment) {
        if (payment.transactionKey() != null) {
            reconcileByTransactionKey(payment);
        } else {
            reconcileByOrderId(payment);
        }
    }

    private void reconcileByTransactionKey(Payment payment) {
        Optional<PgPaymentDto.TransactionResponse> response = pgClient.getTransaction(payment.transactionKey());
        if (response.isEmpty()) {
            log.warn("PG에 트랜잭션 없음 - transactionKey: {}", payment.transactionKey());
            return;
        }
        PgPaymentDto.TransactionResponse pg = response.get();
        paymentApp.applyPgResponse(payment.orderId(), pg.transactionKey(), pg.status().name(), pg.reason());
    }

    private void reconcileByOrderId(Payment payment) {
        Optional<PgPaymentDto.TransactionListResponse> listResponse = pgClient.getTransactionsByOrderId(payment.orderId());
        if (listResponse.isEmpty()) {
            paymentApp.applyPgResponse(payment.orderId(), null, "FAILED", "PG 내역 없음");
            return;
        }

        List<PgPaymentDto.TransactionResponse> transactions = listResponse.get().transactions();

        if (transactions.isEmpty()) {
            paymentApp.applyPgResponse(payment.orderId(), null, "FAILED", "PG 내역 없음");
            return;
        }

        Optional<PgPaymentDto.TransactionResponse> success = transactions.stream()
                .filter(t -> t.status() == PgPaymentDto.TransactionStatus.SUCCESS)
                .findFirst();
        if (success.isPresent()) {
            PgPaymentDto.TransactionResponse pg = success.get();
            paymentApp.applyPgResponse(payment.orderId(), pg.transactionKey(), pg.status().name(), pg.reason());
            return;
        }

        boolean allFailed = transactions.stream()
                .allMatch(t -> t.status() == PgPaymentDto.TransactionStatus.FAILED);
        if (allFailed) {
            PgPaymentDto.TransactionResponse pg = transactions.get(0);
            paymentApp.applyPgResponse(payment.orderId(), pg.transactionKey(), pg.status().name(), pg.reason());
        }
        // PENDING만 있으면 → skip (다음 사이클에서 재확인)
    }
}
