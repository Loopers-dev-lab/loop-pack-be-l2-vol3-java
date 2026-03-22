package com.loopers.infrastructure.payment.pg;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

import com.loopers.domain.payment.OrderTransactionResult;
import com.loopers.domain.payment.PaymentGateway;
import com.loopers.domain.payment.PaymentRequest;
import com.loopers.domain.payment.TransactionDetailResult;
import com.loopers.domain.payment.TransactionResult;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class PaymentGatewayImpl implements PaymentGateway {

    private final PgPaymentHttpInterface pgClient;

    @Override
    public TransactionResult requestPayment(Long userId, PaymentRequest request) {
        try {
            PgApiResponse<PgTransactionResponse> response = pgClient.requestPayment(
                    userId,
                    PgPaymentRequest.from(request)
            );
            return response.data().toDomain();
        } catch (HttpClientErrorException | HttpServerErrorException | ResourceAccessException | CallNotPermittedException e) {
            throw convertException(e);
        }
    }

    @Override
    public TransactionDetailResult getTransaction(Long userId, String transactionKey) {
        try {
            PgApiResponse<PgTransactionDetailResponse> response = pgClient.getTransaction(
                    userId,
                    transactionKey
            );
            return response.data().toDomain();
        } catch (HttpClientErrorException | HttpServerErrorException | ResourceAccessException | CallNotPermittedException e) {
            throw convertException(e);
        }
    }

    @Override
    public OrderTransactionResult getTransactionsByOrder(Long userId, String orderId) {
        try {
            PgApiResponse<PgOrderResponse> response = pgClient.getTransactionsByOrder(
                    userId,
                    orderId
            );
            return response.data().toDomain();
        } catch (HttpClientErrorException | HttpServerErrorException | ResourceAccessException | CallNotPermittedException e) {
            throw convertException(e);
        }
    }

    private CoreException convertException(Exception e) {
        if (e instanceof HttpClientErrorException clientError && clientError.getStatusCode() != HttpStatus.NOT_FOUND) {
            return new CoreException(ErrorType.BAD_REQUEST, clientError.getResponseBodyAsString());
        }
        return new CoreException(ErrorType.PAYMENT_GATEWAY_UNAVAILABLE);
    }
}
