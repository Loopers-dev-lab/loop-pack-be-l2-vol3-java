package com.loopers.application.payment;

import com.loopers.domain.order.Order;
import com.loopers.domain.order.OrderRepository;
import com.loopers.domain.payment.Payment;
import com.loopers.domain.payment.PgClient;
import com.loopers.domain.payment.PgPaymentResponse;
import com.loopers.domain.payment.PaymentRepository;
import com.loopers.support.error.CoreException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentRecoveryScheduler {

    private final PaymentRepository paymentRepository;
    private final OrderRepository orderRepository;
    private final PgClient pgClient;

    @Scheduled(fixedDelay = 3 * 60 * 1000) // 3분마다
    @Transactional
    public void recoverPendingPayments() {
        ZonedDateTime threshold = ZonedDateTime.now().minusSeconds(10);
        List<Payment> pendingPayments = paymentRepository.findPendingBefore(threshold);

        if (pendingPayments.isEmpty()) {
            return;
        }

        log.info("PENDING 결제 복구 시작. 대상 건수={}", pendingPayments.size());

        for (Payment payment : pendingPayments) {
            recoverPayment(payment);
        }
    }

    private void recoverPayment(Payment payment) {
        Order order = orderRepository.findById(payment.getRefOrderId()).orElse(null);
        if (order == null) {
            log.warn("주문을 찾을 수 없습니다. orderId={}", payment.getRefOrderId());
            return;
        }

        List<PgPaymentResponse> pgResponses;
        try {
            pgResponses = pgClient.getPaymentsByOrderId(
                String.valueOf(order.getRefUserId()),
                String.valueOf(order.getId())
            );
        } catch (CoreException e) {
            log.warn("PG 조회 실패로 복구 건너뜀. orderId={}", order.getId());
            return;
        }

        if (pgResponses.isEmpty()) {
            log.info("PG에 결제 정보 없음. orderId={} → FAILED 처리", order.getId());
            payment.markFailed("PG 결제 정보 없음");
            paymentRepository.save(payment);
            return;
        }

        PgPaymentResponse latest = pgResponses.get(pgResponses.size() - 1);

        if (latest.isSuccess()) {
            log.info("PG 결제 성공 확인. orderId={}, transactionKey={}", order.getId(), latest.transactionKey());
            payment.markSuccess(latest.transactionKey());
            paymentRepository.save(payment);
            order.confirm();
            orderRepository.save(order);
        } else if (latest.isFailed()) {
            log.info("PG 결제 실패 확인. orderId={}, reason={}", order.getId(), latest.reason());
            payment.markFailed(latest.reason());
            paymentRepository.save(payment);
        } else {
            log.info("PG 결제 아직 PENDING. orderId={}", order.getId());
        }
    }
}
