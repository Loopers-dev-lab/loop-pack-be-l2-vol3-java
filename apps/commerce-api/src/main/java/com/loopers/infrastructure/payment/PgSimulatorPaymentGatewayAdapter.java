package com.loopers.infrastructure.payment;

import com.loopers.domain.payment.CardType;
import com.loopers.domain.payment.PaymentGateway;
import com.loopers.domain.payment.PaymentGateway.PaymentGatewayCancelRequest;
import com.loopers.domain.payment.PaymentGateway.PaymentGatewayRequest;
import com.loopers.domain.payment.PaymentGateway.PaymentGatewayTransaction;
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
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.net.ConnectException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "loopers.payment.gateway", name = "mode", havingValue = "pg-simulator", matchIfMissing = true)
public class PgSimulatorPaymentGatewayAdapter implements ProviderPaymentGateway {

    private final PgSimulatorPayClient pgSimulatorPayClient;
    private final PgSimulatorCancelClient pgSimulatorCancelClient;
    private final PgSimulatorQueryClient pgSimulatorQueryClient;
    private final PaymentGatewayResilienceExecutor paymentGatewayResilienceExecutor;

    @Override
    public boolean supports(CardType cardType) {
        return cardType == CardType.SAMSUNG || cardType == CardType.KB || cardType == CardType.HYUNDAI;
    }

    @Override
    public PaymentGatewayTransaction requestPayment(PaymentGatewayRequest request) {
        try {
            return paymentGatewayResilienceExecutor.executeRequest(() -> doRequestPayment(request));
        } catch (PaymentGatewayConnectionException | CallNotPermittedException e) {
            return recoverRequestByOrderReferenceOrThrowRecoveryRequired(request.memberId(), request.orderReference());
        }
    }

    @Override
    public PaymentGatewayTransaction cancelPayment(PaymentGatewayCancelRequest request) {
        try {
            return paymentGatewayResilienceExecutor.executeCancel(() -> doCancelPayment(request));
        } catch (PaymentGatewayConnectionException | CallNotPermittedException e) {
            return recoverByTransactionKeyOrThrowRecoveryRequired(request.memberId(), request.transactionKey());
        }
    }

    @Override
    public PaymentGatewayTransaction getPayment(String memberId, String transactionKey) {
        try {
            return paymentGatewayResilienceExecutor.executeQuery(() -> doGetPayment(memberId, transactionKey));
        } catch (PaymentGatewayConnectionException | CallNotPermittedException e) {
            throw new CoreException(ErrorType.INTERNAL_ERROR, "PG 결제 조회 연결에 실패했습니다.");
        }
    }

    @Override
    public List<PaymentGatewayTransaction> getPaymentsByOrderId(String memberId, String orderReference) {
        try {
            return paymentGatewayResilienceExecutor.executeQuery(() -> doGetPaymentsByOrderId(memberId, orderReference));
        } catch (PaymentGatewayConnectionException | CallNotPermittedException e) {
            throw new CoreException(ErrorType.INTERNAL_ERROR, "PG 주문 결제 조회 연결에 실패했습니다.");
        }
    }

    private PaymentGatewayTransaction doRequestPayment(PaymentGatewayRequest request) {
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
            if (isConnectionFailure(e.getCause())) {
                throw new PaymentGatewayConnectionException("PG 결제 요청 연결에 실패했습니다.", e);
            }
            return recoverRequestByOrderReferenceOrThrowRecoveryRequired(request.memberId(), request.orderReference());
        } catch (FeignException.NotFound e) {
            throw new CoreException(ErrorType.NOT_FOUND, "PG 결제 요청 대상을 찾을 수 없습니다.");
        } catch (FeignException.BadRequest e) {
            throw new CoreException(ErrorType.BAD_REQUEST, "PG 결제 요청이 거부되었습니다.");
        } catch (FeignException.Conflict e) {
            throw new CoreException(ErrorType.CONFLICT, "PG 결제 요청이 충돌되었습니다.");
        } catch (FeignException e) {
            if (e.status() == 409) {
                throw new CoreException(ErrorType.CONFLICT, "PG 결제 요청이 충돌되었습니다.");
            }
            if (e.status() >= 400 && e.status() < 500) {
                throw new CoreException(ErrorType.BAD_REQUEST, "PG 결제 요청이 거부되었습니다.");
            }
            if (e.status() >= 500 || e.status() == -1) {
                return recoverRequestByOrderReferenceOrThrowRecoveryRequired(request.memberId(), request.orderReference());
            }
            throw new CoreException(ErrorType.INTERNAL_ERROR, "PG 결제 요청 중 오류가 발생했습니다.");
        }
    }

    private PaymentGatewayTransaction doCancelPayment(PaymentGatewayCancelRequest request) {
        try {
            PgSimulatorApiResponse<PgSimulatorTransactionResponse> response = pgSimulatorCancelClient.cancelPayment(
                    request.memberId(),
                    request.transactionKey()
            );
            return toGatewayTransaction(response.data());
        } catch (RetryableException e) {
            if (isConnectionFailure(e.getCause())) {
                throw new PaymentGatewayConnectionException("PG 결제 취소 요청 연결에 실패했습니다.", e);
            }
            return recoverByTransactionKeyOrThrowRecoveryRequired(request.memberId(), request.transactionKey());
        } catch (FeignException.NotFound e) {
            throw new CoreException(ErrorType.NOT_FOUND, "PG 결제 취소 대상을 찾을 수 없습니다.");
        } catch (FeignException.Conflict e) {
            throw new CoreException(ErrorType.CONFLICT, "PG 결제 취소 요청이 충돌되었습니다.");
        } catch (FeignException e) {
            throw new CoreException(ErrorType.INTERNAL_ERROR, "PG 결제 취소 중 오류가 발생했습니다.");
        }
    }

    private PaymentGatewayTransaction doGetPayment(String memberId, String transactionKey) {
        try {
            PgSimulatorApiResponse<PgSimulatorTransactionResponse> response =
                    pgSimulatorQueryClient.getPayment(memberId, transactionKey);
            return toGatewayTransaction(response.data());
        } catch (RetryableException e) {
            if (isConnectionFailure(e.getCause())) {
                throw new PaymentGatewayConnectionException("PG 결제 조회 연결에 실패했습니다.", e);
            }
            throw new CoreException(ErrorType.INTERNAL_ERROR, "PG 결제 조회 중 오류가 발생했습니다.");
        } catch (FeignException.NotFound e) {
            throw new CoreException(ErrorType.NOT_FOUND, "PG 결제 조회 대상을 찾을 수 없습니다.");
        } catch (FeignException e) {
            throw new CoreException(ErrorType.INTERNAL_ERROR, "PG 결제 조회 중 오류가 발생했습니다.");
        }
    }

    private List<PaymentGatewayTransaction> doGetPaymentsByOrderId(String memberId, String orderReference) {
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
        } catch (RetryableException e) {
            if (isConnectionFailure(e.getCause())) {
                throw new PaymentGatewayConnectionException("PG 주문 결제 조회 연결에 실패했습니다.", e);
            }
            throw new CoreException(ErrorType.INTERNAL_ERROR, "PG 주문 결제 조회 중 오류가 발생했습니다.");
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

    private PaymentGatewayTransaction recoverRequestByOrderReferenceOrThrowRecoveryRequired(String memberId, String orderReference) {
        try {
            List<PaymentGatewayTransaction> transactions = getPaymentsByOrderId(memberId, orderReference);
            if (transactions.isEmpty()) {
                throw new PaymentRecoveryRequiredException("결제 요청 결과가 불명확하고 조회 결과도 없습니다.");
            }
            if (transactions.size() != 1) {
                throw new PaymentRecoveryRequiredException("결제 요청 결과가 불명확합니다. 주문 결제 내역이 여러 건 조회됩니다.");
            }
            return transactions.get(0);
        } catch (CoreException e) {
            throw new PaymentRecoveryRequiredException("결제 요청 결과가 불명확하며 상태 조회에도 실패했습니다.");
        }
    }

    private PaymentGatewayTransaction recoverByTransactionKeyOrThrowRecoveryRequired(String memberId, String transactionKey) {
        try {
            return getPayment(memberId, transactionKey);
        } catch (CoreException e) {
            throw new PaymentRecoveryRequiredException("결제 취소 결과가 불명확하며 상태 조회에도 실패했습니다.");
        }
    }
}
