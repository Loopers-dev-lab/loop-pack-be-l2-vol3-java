package com.loopers.infrastructure.payment;

import com.loopers.domain.payment.CardType;
import com.loopers.domain.payment.PaymentGateway;
import com.loopers.domain.payment.PaymentStatus;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Component
@ConditionalOnProperty(prefix = "loopers.payment.gateway", name = "mode", havingValue = "fake-success")
public class FakeSuccessPaymentGatewayAdapter implements ProviderPaymentGateway {

    private final AtomicLong sequence = new AtomicLong();
    private final Map<String, PaymentGateway.PaymentGatewayTransaction> byTransactionKey = new ConcurrentHashMap<>();

    @Override
    public boolean supports(CardType cardType) {
        return true;
    }

    @Override
    public PaymentGateway.PaymentGatewayTransaction requestPayment(PaymentGateway.PaymentGatewayRequest request) {
        String transactionKey = "FAKE-PG-" + sequence.incrementAndGet();
        PaymentGateway.PaymentGatewayTransaction tx = new PaymentGateway.PaymentGatewayTransaction(
                transactionKey,
                request.orderReference(),
                PaymentStatus.REQUESTED,
                null
        );
        byTransactionKey.put(transactionKey, tx);
        return tx;
    }

    @Override
    public PaymentGateway.PaymentGatewayTransaction cancelPayment(PaymentGateway.PaymentGatewayCancelRequest request) {
        PaymentGateway.PaymentGatewayTransaction current = byTransactionKey.get(request.transactionKey());
        PaymentGateway.PaymentGatewayTransaction cancelled = new PaymentGateway.PaymentGatewayTransaction(
                request.transactionKey(),
                current == null ? "UNKNOWN-ORDER" : current.orderReference(),
                PaymentStatus.CANCELLED,
                null
        );
        byTransactionKey.put(request.transactionKey(), cancelled);
        return cancelled;
    }

    @Override
    public PaymentGateway.PaymentGatewayTransaction getPayment(String memberId, String transactionKey) {
        PaymentGateway.PaymentGatewayTransaction current = byTransactionKey.get(transactionKey);
        if (current == null) {
            return new PaymentGateway.PaymentGatewayTransaction(transactionKey, "UNKNOWN-ORDER", PaymentStatus.SUCCEEDED, null);
        }
        if (current.status() == PaymentStatus.REQUESTED) {
            PaymentGateway.PaymentGatewayTransaction succeeded = new PaymentGateway.PaymentGatewayTransaction(
                    current.transactionKey(),
                    current.orderReference(),
                    PaymentStatus.SUCCEEDED,
                    null
            );
            byTransactionKey.put(transactionKey, succeeded);
            return succeeded;
        }
        return current;
    }

    @Override
    public List<PaymentGateway.PaymentGatewayTransaction> getPaymentsByOrderId(String memberId, String orderReference) {
        return byTransactionKey.values().stream()
                .filter(tx -> orderReference.equals(tx.orderReference()))
                .map(tx -> tx.status() == PaymentStatus.REQUESTED
                        ? new PaymentGateway.PaymentGatewayTransaction(tx.transactionKey(), tx.orderReference(), PaymentStatus.SUCCEEDED, null)
                        : tx)
                .toList();
    }
}
