package com.loopers.application.payment;

import com.loopers.application.order.OrderService;
import com.loopers.domain.payment.Payment;
import com.loopers.domain.payment.PaymentRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Service
public class PaymentFacade {
    private final PaymentRepository paymentRepository;
    private final OrderService orderService;

    @Transactional
    public void handleCallback(PgCallbackCommand command) {
        Payment payment = paymentRepository.findByPgTransactionId(command.transactionKey())
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "결제건이 존재하지 않습니다. transactionKey: " + command.transactionKey()));

        switch (command.status()) {
            case "SUCCESS" -> {
                payment.complete();
                orderService.markOrderPaid(payment.getOrderId());
            }
            case "FAILED" -> {
                payment.fail(command.reason());
                orderService.markOrderFailed(payment.getOrderId());
            }

            default -> throw new CoreException(ErrorType.BAD_REQUEST, "처리할 수 없는 PG 상태입니다: " + command.status());
        }
    }
}
