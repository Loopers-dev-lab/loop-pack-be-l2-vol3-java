package com.loopers.application.payment;

import com.loopers.domain.payment.gateway.PaymentGateway;
import com.loopers.domain.payment.gateway.PgType;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class PaymentGatewayRegistry {

    private final Map<PgType, PaymentGateway> gateways;
    private final CircuitBreakerRegistry circuitBreakerRegistry;

    public PaymentGatewayRegistry(
            List<PaymentGateway> gatewayList,
            CircuitBreakerRegistry circuitBreakerRegistry) {
        this.gateways = gatewayList.stream()
                .collect(Collectors.toMap(PaymentGateway::getType, g -> g));
        this.circuitBreakerRegistry = circuitBreakerRegistry;
    }

    public PaymentGateway getGateway(PgType type) {
        PaymentGateway gateway = gateways.get(type);
        if (gateway == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "지원하지 않는 결제수단입니다: " + type);
        }
        return gateway;
    }

    public List<PgType> getAvailableTypes() {
        return gateways.values().stream()
                .filter(gw -> {
                    CircuitBreaker cb = circuitBreakerRegistry
                            .circuitBreaker(gw.getCircuitBreakerName());
                    return cb.getState() != CircuitBreaker.State.OPEN;
                })
                .map(PaymentGateway::getType)
                .toList();
    }
}
