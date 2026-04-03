package com.loopers.application.payment;

import com.loopers.domain.payment.Payment;
import com.loopers.domain.payment.PaymentGateway;
import com.loopers.domain.payment.PaymentRepository;
import com.loopers.domain.payment.PaymentStatus;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PaymentStartCompletionApplicationService {

    private final PaymentRepository paymentRepository;

    @Transactional
    public Payment complete(Payment requested, PaymentGateway.PaymentGatewayTransaction gatewayResult) {
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
            return paymentRepository.findByMemberIdAndOrderId(requested.memberId(), requested.orderId())
                    .orElseThrow(() -> new CoreException(
                            ErrorType.CONFLICT,
                            "결제를 갱신하지 못했습니다. 잠시 후 다시 시도해 주세요."
                    ));
        }
    }
}
