package com.loopers.application.payment;

import org.springframework.transaction.annotation.Transactional;

import com.loopers.application.shared.annotation.UseCase;
import com.loopers.domain.payment.Payment;
import com.loopers.domain.payment.PaymentService;
import com.loopers.support.error.CoreException;

import lombok.RequiredArgsConstructor;

/**
 * PG로부터 수신한 결제 콜백을 처리한다.
 *
 * <p>결제 상태를 변경하고, 결제 결과에 따라 주문 상태를 변경한다.
 * 결제 실패 시 재고/쿠폰 복원은 {@link com.loopers.domain.order.OrderEvent.OrderFailed}
 * 이벤트를 통해 비동기로 처리된다.
 * 이미 처리된 결제는 멱등성을 위해 무시한다.</p>
 */
@UseCase
@RequiredArgsConstructor
public class HandlePaymentCallbackUseCase {

    private final PaymentService paymentService;

    /**
     * @param command 결제 콜백 커맨드 (transactionKey, status, reason)
     * @throws CoreException 결제가 존재하지 않는 경우
     */
    @Transactional
    public void execute(PaymentCallbackCommand command) {
        Payment payment = paymentService.getByTransactionKey(command.transactionKey());

        if (payment.isProcessed()) {
            return;
        }

        switch (command.status()) {
            case SUCCESS -> paymentService.success(payment.getId(), command.reason());
            case FAILED -> paymentService.fail(payment.getId(), command.reason());
        }
    }
}
