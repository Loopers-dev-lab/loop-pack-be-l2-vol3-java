package com.loopers.application.payment;

import com.loopers.application.payment.command.CompletePaymentCommand;
import com.loopers.application.outbox.OrderPaymentOutboxService;
import com.loopers.application.outbox.PaymentStatusChangedOutboxMessage;
import com.loopers.application.payment.event.PaymentStatusChangedEvent;
import com.loopers.domain.payment.Payment;
import com.loopers.domain.payment.PaymentGateway;
import com.loopers.domain.payment.PaymentRepository;
import com.loopers.domain.payment.PaymentStatus;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class PaymentCompletionApplicationService {

    private final PaymentRepository paymentRepository;
    private final PaymentGateway paymentGateway;
    private final ApplicationEventPublisher applicationEventPublisher;
    private final OrderPaymentOutboxService orderPaymentOutboxService;

    @Transactional
    public Payment complete(CompletePaymentCommand command) {
        Payment payment = paymentRepository.findByPgTransactionKey(command.memberId(), command.transactionKey())
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "결제 내역을 찾을 수 없습니다."));

        PaymentGateway.PaymentGatewayTransaction gatewayResult =
                paymentGateway.getPayment(command.memberId(), command.transactionKey());

        Payment resolved = switch (gatewayResult.status()) {
            case REQUESTED -> payment;
            case SUCCEEDED -> {
                if (payment.status() == PaymentStatus.REQUESTED) {
                    yield payment.markSucceeded(gatewayResult.transactionKey());
                }
                yield payment;
            }
            case FAILED -> {
                if (payment.status() == PaymentStatus.REQUESTED) {
                    yield payment.markFailed(
                            gatewayResult.reason() == null || gatewayResult.reason().isBlank()
                                    ? "결제 실패"
                                    : gatewayResult.reason()
                    );
                }
                yield payment;
            }
            case CANCEL_REQUESTED -> {
                if (payment.status() == PaymentStatus.REQUESTED
                        || payment.status() == PaymentStatus.SUCCEEDED
                        || payment.status() == PaymentStatus.CANCEL_FAILED) {
                    yield payment.requestCancel();
                }
                yield payment;
            }
            case CANCEL_RECONCILE_REQUIRED -> payment;
            case CANCELLED -> {
                if (payment.status() == PaymentStatus.REQUESTED) {
                    yield payment.requestCancel().markCancelled();
                }
                if (payment.status() == PaymentStatus.CANCEL_REQUESTED
                        || payment.status() == PaymentStatus.CANCEL_RECONCILE_REQUIRED) {
                    yield payment.markCancelled();
                }
                yield payment;
            }
            case CANCEL_FAILED -> {
                if (payment.status() == PaymentStatus.REQUESTED) {
                    yield payment.requestCancel().markCancelFailed(
                            gatewayResult.reason() == null || gatewayResult.reason().isBlank()
                                    ? "결제 취소 실패"
                                    : gatewayResult.reason()
                    );
                }
                if (payment.status() == PaymentStatus.CANCEL_REQUESTED
                        || payment.status() == PaymentStatus.CANCEL_RECONCILE_REQUIRED) {
                    yield payment.markCancelFailed(
                            gatewayResult.reason() == null || gatewayResult.reason().isBlank()
                                    ? "결제 취소 실패"
                                    : gatewayResult.reason()
                    );
                }
                yield payment;
            }
        };

        if (resolved.equals(payment)) {
            return payment;
        }
        Payment saved = paymentRepository.save(resolved);
        if (saved.status() != payment.status()) {
            Instant changedAt = Instant.now();
            applicationEventPublisher.publishEvent(
                    new PaymentStatusChangedEvent(saved.memberId(), saved.orderId(), payment.status(), saved.status(), changedAt)
            );
            orderPaymentOutboxService.savePaymentStatusChanged(new PaymentStatusChangedOutboxMessage(
                    java.util.UUID.randomUUID(),
                    saved.orderId(),
                    saved.memberId(),
                    payment.status(),
                    saved.status(),
                    changedAt
            ));
        }
        return saved;
    }
}
