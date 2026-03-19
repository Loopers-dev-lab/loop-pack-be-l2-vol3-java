package com.loopers.infrastructure.payment;

import com.loopers.domain.payment.CardType;
import com.loopers.domain.payment.PaymentGateway;
import com.loopers.domain.payment.PaymentStatus;
import com.loopers.infrastructure.payment.pgsimulator.PgSimulatorCancelClient;
import com.loopers.infrastructure.payment.pgsimulator.PgSimulatorPayClient;
import com.loopers.infrastructure.payment.pgsimulator.PgSimulatorQueryClient;
import com.loopers.infrastructure.payment.pgsimulator.dto.PgSimulatorApiResponse;
import com.loopers.infrastructure.payment.pgsimulator.dto.PgSimulatorOrderTransactionsResponse;
import com.loopers.infrastructure.payment.pgsimulator.dto.PgSimulatorRequestPaymentRequest;
import com.loopers.infrastructure.payment.pgsimulator.dto.PgSimulatorTransactionResponse;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import feign.FeignException;
import feign.RetryableException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.net.ConnectException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Component
@RequiredArgsConstructor
public class PgSimulatorPaymentGatewayAdapter implements ProviderPaymentGateway {

    private static final int CONNECTION_RETRY_MAX_ATTEMPTS = 3;

    private final PgSimulatorPayClient pgSimulatorPayClient;
    private final PgSimulatorCancelClient pgSimulatorCancelClient;
    private final PgSimulatorQueryClient pgSimulatorQueryClient;

    @Override
    public boolean supports(CardType cardType) {
        return cardType == CardType.SAMSUNG || cardType == CardType.KB || cardType == CardType.HYUNDAI;
    }

    @Override
    public PaymentGatewayTransaction requestPayment(PaymentGatewayRequest request) {
        for (int attempt = 1; attempt <= CONNECTION_RETRY_MAX_ATTEMPTS; attempt++) {
            try {
                PgSimulatorApiResponse<PgSimulatorTransactionResponse> response = pgSimulatorPayClient.requestPayment(
                        request.memberId(),
                        new PgSimulatorRequestPaymentRequest(
                                request.orderReference(),
                                request.cardType().name(),
                                request.cardNo(),
                                request.amount(),
                                request.callbackUrl()
                        )
                );
                return toGatewayTransaction(response.data());
            } catch (RetryableException e) {
                if (!isConnectionFailure(e.getCause())) {
                    return recoverRequestByOrderReference(request.memberId(), request.orderReference());
                }
                if (attempt == CONNECTION_RETRY_MAX_ATTEMPTS) {
                    throw new CoreException(ErrorType.INTERNAL_ERROR, "PG 결제 요청 연결에 실패했습니다.");
                }
            } catch (FeignException.NotFound e) {
                throw new CoreException(ErrorType.NOT_FOUND, "PG 결제 요청 대상을 찾을 수 없습니다.");
            } catch (FeignException.BadRequest e) {
                throw new CoreException(ErrorType.BAD_REQUEST, "PG 결제 요청이 거부되었습니다.");
            } catch (FeignException e) {
                throw new CoreException(ErrorType.INTERNAL_ERROR, "PG 결제 요청 중 오류가 발생했습니다.");
            }
        }

        throw new CoreException(ErrorType.INTERNAL_ERROR, "PG 결제 요청 연결에 실패했습니다.");
    }

    @Override
    public PaymentGatewayTransaction cancelPayment(PaymentGatewayCancelRequest request) {
        for (int attempt = 1; attempt <= CONNECTION_RETRY_MAX_ATTEMPTS; attempt++) {
            try {
                PgSimulatorApiResponse<PgSimulatorTransactionResponse> response = pgSimulatorCancelClient.cancelPayment(
                        request.memberId(),
                        request.transactionKey()
                );
                return toGatewayTransaction(response.data());
            } catch (RetryableException e) {
                if (!isConnectionFailure(e.getCause())) {
                    return recoverByTransactionKey(request.memberId(), request.transactionKey());
                }
                if (attempt == CONNECTION_RETRY_MAX_ATTEMPTS) {
                    throw new CoreException(ErrorType.INTERNAL_ERROR, "PG 결제 취소 요청 연결에 실패했습니다.");
                }
            } catch (FeignException.NotFound e) {
                throw new CoreException(ErrorType.NOT_FOUND, "PG 결제 취소 대상을 찾을 수 없습니다.");
            } catch (FeignException.Conflict e) {
                throw new CoreException(ErrorType.CONFLICT, "PG 결제 취소 요청이 충돌되었습니다.");
            } catch (FeignException e) {
                throw new CoreException(ErrorType.INTERNAL_ERROR, "PG 결제 취소 중 오류가 발생했습니다.");
            }
        }

        throw new CoreException(ErrorType.INTERNAL_ERROR, "PG 결제 취소 요청 연결에 실패했습니다.");
    }

    @Override
    public PaymentGatewayTransaction getPayment(String memberId, String transactionKey) {
        try {
            PgSimulatorApiResponse<PgSimulatorTransactionResponse> response =
                    pgSimulatorQueryClient.getPayment(memberId, transactionKey);
            return toGatewayTransaction(response.data());
        } catch (FeignException.NotFound e) {
            throw new CoreException(ErrorType.NOT_FOUND, "PG 결제 조회 대상을 찾을 수 없습니다.");
        } catch (FeignException e) {
            throw new CoreException(ErrorType.INTERNAL_ERROR, "PG 결제 조회 중 오류가 발생했습니다.");
        }
    }

    @Override
    public List<PaymentGatewayTransaction> getPaymentsByOrderId(String memberId, String orderReference) {
        try {
            PgSimulatorApiResponse<PgSimulatorOrderTransactionsResponse> response =
                    pgSimulatorQueryClient.getPaymentsByOrderId(memberId, orderReference);
            if (response.data() == null || response.data().transactions() == null) {
                return List.of();
            }

            List<PaymentGatewayTransaction> transactions = new ArrayList<>();
            for (PgSimulatorTransactionResponse transaction : response.data().transactions()) {
                transactions.add(toGatewayTransaction(transaction));
            }
            return transactions;
        } catch (FeignException.NotFound e) {
            throw new CoreException(ErrorType.NOT_FOUND, "PG 주문 결제 조회 대상을 찾을 수 없습니다.");
        } catch (FeignException e) {
            throw new CoreException(ErrorType.INTERNAL_ERROR, "PG 주문 결제 조회 중 오류가 발생했습니다.");
        }
    }

    private PaymentGatewayTransaction toGatewayTransaction(PgSimulatorTransactionResponse response) {
        if (response == null) {
            throw new CoreException(ErrorType.INTERNAL_ERROR, "PG 응답 데이터가 비어있습니다.");
        }
        return new PaymentGatewayTransaction(
                response.transactionKey(),
                response.orderId(),
                toPaymentStatus(response.status()),
                response.reason()
        );
    }

    private PaymentStatus toPaymentStatus(String status) {
        if (status == null || status.isBlank()) {
            throw new CoreException(ErrorType.INTERNAL_ERROR, "PG 결제 상태값이 비어있습니다.");
        }

        String normalized = status.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "PENDING", "REQUESTED" -> PaymentStatus.REQUESTED;
            case "SUCCESS", "SUCCEEDED" -> PaymentStatus.SUCCEEDED;
            case "FAILED" -> PaymentStatus.FAILED;
            case "CANCEL_REQUESTED" -> PaymentStatus.CANCEL_REQUESTED;
            case "CANCELLED" -> PaymentStatus.CANCELLED;
            case "CANCEL_FAILED" -> PaymentStatus.CANCEL_FAILED;
            default -> throw new CoreException(ErrorType.INTERNAL_ERROR, "지원하지 않는 PG 결제 상태값입니다. (status: " + status + ")");
        };
    }

    private boolean isConnectionFailure(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof ConnectException || current instanceof UnknownHostException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private PaymentGatewayTransaction recoverRequestByOrderReference(String memberId, String orderReference) {
        try {
            List<PaymentGatewayTransaction> transactions = getPaymentsByOrderId(memberId, orderReference);
            if (transactions.isEmpty()) {
                throw new CoreException(ErrorType.INTERNAL_ERROR, "결제 요청 결과가 불명확하고 조회 결과도 없습니다.");
            }
            return transactions.get(0);
        } catch (CoreException e) {
            throw new CoreException(ErrorType.INTERNAL_ERROR, "결제 요청 결과가 불명확하며 상태 조회에도 실패했습니다.");
        }
    }

    private PaymentGatewayTransaction recoverByTransactionKey(String memberId, String transactionKey) {
        try {
            return getPayment(memberId, transactionKey);
        } catch (CoreException e) {
            throw new PaymentRecoveryRequiredException("결제 취소 결과가 불명확하며 상태 조회에도 실패했습니다.");
        }
    }
}
