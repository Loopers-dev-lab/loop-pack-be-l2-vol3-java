package com.loopers.interfaces.payment;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/circuit-breakers")
@RequiredArgsConstructor
public class CircuitBreakerMonitorController {

    private final CircuitBreakerRegistry circuitBreakerRegistry;

    @GetMapping
    public List<CircuitBreakerStatus> getAllStatus() {
        return circuitBreakerRegistry.getAllCircuitBreakers()
                .stream()
                .map(this::toStatus)
                .toList();
    }

    private CircuitBreakerStatus toStatus(CircuitBreaker cb) {
        var metrics = cb.getMetrics();
        return new CircuitBreakerStatus(
                cb.getName(),
                cb.getState().name(),
                metrics.getFailureRate(),
                metrics.getNumberOfBufferedCalls(),
                metrics.getNumberOfFailedCalls(),
                metrics.getNumberOfSuccessfulCalls(),
                metrics.getNumberOfNotPermittedCalls());
    }

    public record CircuitBreakerStatus(
            String name,
            String state,
            float failureRate,
            int bufferedCalls,
            int failedCalls,
            int successfulCalls,
            long notPermittedCalls) {
    }
}
