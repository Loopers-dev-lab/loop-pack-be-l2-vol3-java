package com.loopers.application.payment;

import com.loopers.application.payment.command.StartPaymentCommand;
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
import org.springframework.dao.DataIntegrityViolationException;

@Service
@RequiredArgsConstructor
public class PaymentStartApplicationService {

    private final PaymentRepository paymentRepository;
    private final PaymentGateway paymentGateway;

    @Transactional
    public Payment start(StartPaymentCommand command) {
        Payment existing = paymentRepository.findByMemberIdAndOrderId(command.memberId(), command.orderId())
                .orElse(null);
        if (existing != null) {
            if (existing.status() == PaymentStatus.REQUESTED
                    || existing.status() == PaymentStatus.SUCCEEDED
                    || existing.status() == PaymentStatus.CANCEL_REQUESTED
                    || existing.status() == PaymentStatus.CANCEL_RECONCILE_REQUIRED
                    || existing.status() == PaymentStatus.CANCELLED) {
                return existing;
            }
        }

        Payment paymentToRequest = existing != null
                ? existing
                : new Payment(
                command.memberId(),
                command.orderId(),
                command.cardType(),
                command.cardNo(),
                command.amount()
        );

        Payment requested;
        try {
            requested = paymentRepository.save(paymentToRequest);
        } catch (DataIntegrityViolationException e) {
            return paymentRepository.findByMemberIdAndOrderId(command.memberId(), command.orderId())
                    .orElseThrow(() -> new CoreException(
                            ErrorType.CONFLICT,
                            "결제를 생성하지 못했습니다. 잠시 후 다시 시도해 주세요."
                    ));
        }

        PaymentGateway.PaymentGatewayTransaction gatewayResult;
        try {
            gatewayResult = paymentGateway.requestPayment(
                    new PaymentGateway.PaymentGatewayRequest(
                            command.memberId(),
                            command.orderId().toString(),
                            command.cardType(),
                            command.cardNo(),
                            command.amount(),
                            command.callbackUrl()
                    )
            );
        } catch (PaymentRecoveryRequiredException e) {
            return requested;
        }

        Payment resolved = switch (gatewayResult.status()) {
            case REQUESTED -> new Payment(
                    requested.id(),
                    requested.memberId(),
                    requested.orderId(),
                    requested.cardType(),
                    requested.cardNo(),
                    requested.amount(),
                    PaymentStatus.REQUESTED,
                    gatewayResult.transactionKey(),
                    requested.reason(),
                    requested.createdAt(),
                    requested.updatedAt(),
                    requested.deletedAt()
            );
            case SUCCEEDED -> requested.markSucceeded(gatewayResult.transactionKey());
            case FAILED -> requested.markFailed(
                    gatewayResult.reason() == null || gatewayResult.reason().isBlank()
                            ? "결제 실패"
                            : gatewayResult.reason()
            );
            default -> throw new CoreException(
                    ErrorType.CONFLICT,
                    "결제 시작 결과 상태가 유효하지 않습니다. (status: " + gatewayResult.status() + ")"
            );
        };

        try {
            return paymentRepository.save(resolved);
        } catch (DataIntegrityViolationException e) {
            return paymentRepository.findByMemberIdAndOrderId(command.memberId(), command.orderId())
                    .orElseThrow(() -> new CoreException(
                            ErrorType.CONFLICT,
                            "결제를 갱신하지 못했습니다. 잠시 후 다시 시도해 주세요."
                    ));
        }
    }
}
