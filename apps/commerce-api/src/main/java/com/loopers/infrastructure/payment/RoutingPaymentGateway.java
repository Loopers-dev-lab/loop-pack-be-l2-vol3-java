package com.loopers.infrastructure.payment;

import com.loopers.domain.payment.PaymentGateway;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class RoutingPaymentGateway implements PaymentGateway {

    private final List<ProviderPaymentGateway> delegates;

    public RoutingPaymentGateway(List<ProviderPaymentGateway> delegates) {
        this.delegates = delegates;
    }

    @Override
    public PaymentGatewayTransaction requestPayment(PaymentGatewayRequest request) {
        return delegates.stream()
                .filter(delegate -> delegate.supports(request.cardType()))
                .findFirst()
                .orElseThrow(() -> new CoreException(
                        ErrorType.BAD_REQUEST,
                        "요청 카드 타입을 지원하는 PG 게이트웨이가 없습니다. (cardType: " + request.cardType() + ")"
                ))
                .requestPayment(request);
    }

    @Override
    public PaymentGatewayTransaction cancelPayment(PaymentGatewayCancelRequest request) {
        if (delegates.isEmpty()) {
            throw new CoreException(ErrorType.INTERNAL_ERROR, "PG 게이트웨이 구현체가 없습니다.");
        }

        CoreException lastError = null;
        for (ProviderPaymentGateway delegate : delegates) {
            try {
                return delegate.cancelPayment(request);
            } catch (CoreException e) {
                lastError = e;
                if (e.getErrorType() != ErrorType.NOT_FOUND) {
                    throw e;
                }
            }
        }

        if (lastError != null) {
            throw lastError;
        }
        throw new CoreException(ErrorType.NOT_FOUND, "취소할 결제 대상을 찾을 수 없습니다.");
    }

    @Override
    public PaymentGatewayTransaction getPayment(String memberId, String transactionKey) {
        if (delegates.isEmpty()) {
            throw new CoreException(ErrorType.INTERNAL_ERROR, "PG 게이트웨이 구현체가 없습니다.");
        }

        CoreException lastError = null;
        for (ProviderPaymentGateway delegate : delegates) {
            try {
                return delegate.getPayment(memberId, transactionKey);
            } catch (CoreException e) {
                lastError = e;
                if (e.getErrorType() != ErrorType.NOT_FOUND) {
                    throw e;
                }
            }
        }

        if (lastError != null) {
            throw lastError;
        }
        throw new CoreException(ErrorType.NOT_FOUND, "조회할 결제 대상을 찾을 수 없습니다.");
    }

    @Override
    public List<PaymentGatewayTransaction> getPaymentsByOrderId(String memberId, String orderReference) {
        if (delegates.isEmpty()) {
            throw new CoreException(ErrorType.INTERNAL_ERROR, "PG 게이트웨이 구현체가 없습니다.");
        }

        CoreException lastError = null;
        for (ProviderPaymentGateway delegate : delegates) {
            try {
                return delegate.getPaymentsByOrderId(memberId, orderReference);
            } catch (CoreException e) {
                lastError = e;
                if (e.getErrorType() != ErrorType.NOT_FOUND) {
                    throw e;
                }
            }
        }

        if (lastError != null) {
            throw lastError;
        }
        throw new CoreException(ErrorType.NOT_FOUND, "주문 결제 내역을 찾을 수 없습니다.");
    }
}
