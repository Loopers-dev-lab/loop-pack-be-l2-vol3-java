package com.loopers.application.payment;

import com.loopers.application.payment.command.CancelPaymentCommand;
import com.loopers.domain.payment.Payment;
import com.loopers.domain.payment.PaymentGateway;
import com.loopers.domain.payment.PaymentRepository;
import com.loopers.domain.payment.PaymentStatus;
import com.loopers.infrastructure.payment.PaymentRecoveryRequiredException;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PaymentCancelApplicationService {

    private final PaymentRepository paymentRepository;
    private final PaymentGateway paymentGateway;

    @Transactional
    public Payment cancel(CancelPaymentCommand command) {
        Payment payment = paymentRepository.findByMemberIdAndOrderId(command.memberId(), command.orderId())
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "결제 내역을 찾을 수 없습니다."));

        if (payment.status() == PaymentStatus.CANCELLED
                || payment.status() == PaymentStatus.CANCEL_REQUESTED
                || payment.status() == PaymentStatus.CANCEL_RECONCILE_REQUIRED) {
            return payment;
        }

        Payment cancelRequested = payment.requestCancel();
        Payment saved = paymentRepository.save(cancelRequested);

        if (saved.pgTransactionKey() == null || saved.pgTransactionKey().isBlank()) {
            throw new CoreException(ErrorType.CONFLICT, "취소 가능한 결제 거래 키가 없습니다.");
        }

        PaymentGateway.PaymentGatewayTransaction gatewayResult;
        try {
            gatewayResult = paymentGateway.cancelPayment(
                    new PaymentGateway.PaymentGatewayCancelRequest(command.memberId(), saved.pgTransactionKey())
            );
        } catch (PaymentRecoveryRequiredException e) {
            Payment reconcileRequired = saved.markCancelReconcileRequired(e.getMessage());
            return paymentRepository.save(reconcileRequired);
        }

        Payment resolved = switch (gatewayResult.status()) {
            case CANCEL_REQUESTED -> saved;
            case CANCELLED -> saved.markCancelled();
            case CANCEL_FAILED -> saved.markCancelFailed(
                    gatewayResult.reason() == null || gatewayResult.reason().isBlank()
                            ? "결제 취소 실패"
                            : gatewayResult.reason()
            );
            default -> throw new CoreException(
                    ErrorType.CONFLICT,
                    "결제 취소 결과 상태가 유효하지 않습니다. (status: " + gatewayResult.status() + ")"
            );
        };

        return paymentRepository.save(resolved);
    }
}
