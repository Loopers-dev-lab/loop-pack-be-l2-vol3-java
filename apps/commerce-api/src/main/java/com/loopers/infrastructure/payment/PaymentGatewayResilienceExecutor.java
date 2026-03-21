package com.loopers.infrastructure.payment;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryRegistry;
import org.springframework.stereotype.Component;

import java.util.function.Supplier;

@Component
public class PaymentGatewayResilienceExecutor {

    private final CircuitBreaker requestCircuitBreaker;
    private final CircuitBreaker cancelCircuitBreaker;
    private final CircuitBreaker queryCircuitBreaker;
    private final Retry requestConnectionRetry;
    private final Retry cancelConnectionRetry;
    private final Retry queryConnectionRetry;

    public PaymentGatewayResilienceExecutor(CircuitBreakerRegistry circuitBreakerRegistry, RetryRegistry retryRegistry) {
        this.requestCircuitBreaker = circuitBreakerRegistry.circuitBreaker("pg-request");
        this.cancelCircuitBreaker = circuitBreakerRegistry.circuitBreaker("pg-cancel");
        this.queryCircuitBreaker = circuitBreakerRegistry.circuitBreaker("pg-query");
        this.requestConnectionRetry = retryRegistry.retry("pg-request-connection");
        this.cancelConnectionRetry = retryRegistry.retry("pg-cancel-connection");
        this.queryConnectionRetry = retryRegistry.retry("pg-query-connection");
    }

    public <T> T executeRequest(Supplier<T> supplier) {
        Supplier<T> retryDecorated = Retry.decorateSupplier(requestConnectionRetry, supplier);
        Supplier<T> circuitBreakerDecorated = CircuitBreaker.decorateSupplier(requestCircuitBreaker, retryDecorated);
        return circuitBreakerDecorated.get();
    }

    public <T> T executeCancel(Supplier<T> supplier) {
        Supplier<T> retryDecorated = Retry.decorateSupplier(cancelConnectionRetry, supplier);
        Supplier<T> circuitBreakerDecorated = CircuitBreaker.decorateSupplier(cancelCircuitBreaker, retryDecorated);
        return circuitBreakerDecorated.get();
    }

    public <T> T executeQuery(Supplier<T> supplier) {
        Supplier<T> retryDecorated = Retry.decorateSupplier(queryConnectionRetry, supplier);
        Supplier<T> circuitBreakerDecorated = CircuitBreaker.decorateSupplier(queryCircuitBreaker, retryDecorated);
        return circuitBreakerDecorated.get();
    }
}
