package com.loopers.infrastructure.pg;

import com.loopers.application.payment.PgPaymentGateway;
import com.loopers.application.payment.PgPaymentRequest;
import com.loopers.application.payment.PgPaymentRequestResult;
import com.loopers.application.payment.PgPaymentSnapshot;
import com.loopers.application.payment.PgPaymentStatus;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class PgSimulatorGateway implements PgPaymentGateway {

    @Qualifier("pgRestTemplate")
    private final RestTemplate restTemplate;
    private final PgSimulatorProperties properties;

    @Override
    @CircuitBreaker(name = "pgCircuit", fallbackMethod = "requestPaymentFallback")
    public PgPaymentRequestResult requestPayment(PgPaymentRequest request) {
        HttpHeaders headers = defaultHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> body = Map.of(
            "orderId", String.valueOf(request.orderId()),
            "cardType", request.cardType().name(),
            "cardNo", request.cardNo(),
            "amount", String.valueOf(request.amount()),
            "callbackUrl", properties.callbackUrl()
        );

        ResponseEntity<Map> response = restTemplate.exchange(
            properties.baseUrl() + "/api/v1/payments",
            HttpMethod.POST,
            new HttpEntity<>(body, headers),
            Map.class
        );

        String paymentKey = extractText(response.getBody(), "paymentKey", "transactionKey", "key", "id");
        if (paymentKey == null || paymentKey.isBlank()) {
            throw new CoreException(ErrorType.INTERNAL_ERROR, "PG 결제 요청 응답에 결제 키가 없습니다.");
        }

        return new PgPaymentRequestResult(paymentKey);
    }

    @Override
    @CircuitBreaker(name = "pgCircuit", fallbackMethod = "getPaymentByKeyFallback")
    public Optional<PgPaymentSnapshot> getPaymentByKey(String paymentKey) {
        HttpHeaders headers = defaultHeaders();
        ResponseEntity<Map> response = restTemplate.exchange(
            properties.baseUrl() + "/api/v1/payments/" + paymentKey,
            HttpMethod.GET,
            new HttpEntity<>(headers),
            Map.class
        );
        return Optional.ofNullable(toSnapshot(response.getBody()));
    }

    @Override
    @CircuitBreaker(name = "pgCircuit", fallbackMethod = "getPaymentByOrderIdFallback")
    public Optional<PgPaymentSnapshot> getPaymentByOrderId(Long orderId) {
        HttpHeaders headers = defaultHeaders();
        ResponseEntity<Object> response = restTemplate.exchange(
            properties.baseUrl() + "/api/v1/payments?orderId=" + orderId,
            HttpMethod.GET,
            new HttpEntity<>(headers),
            Object.class
        );

        Object body = response.getBody();
        if (body instanceof List<?> list) {
            return list.stream()
                .filter(Map.class::isInstance)
                .map(Map.class::cast)
                .findFirst()
                .map(this::toSnapshot);
        }

        if (body instanceof Map<?, ?> mapBody) {
            return Optional.ofNullable(toSnapshot((Map<?, ?>) mapBody));
        }

        return Optional.empty();
    }

    private PgPaymentRequestResult requestPaymentFallback(PgPaymentRequest request, Throwable t) {
        throw toExternalError("PG 결제 요청에 실패했습니다.", t);
    }

    private Optional<PgPaymentSnapshot> getPaymentByKeyFallback(String paymentKey, Throwable t) {
        return Optional.empty();
    }

    private Optional<PgPaymentSnapshot> getPaymentByOrderIdFallback(Long orderId, Throwable t) {
        return Optional.empty();
    }

    private HttpHeaders defaultHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-USER-ID", properties.userId());
        return headers;
    }

    private PgPaymentSnapshot toSnapshot(Map<?, ?> map) {
        if (map == null || map.isEmpty()) {
            return null;
        }

        Long orderId = extractLong(map, "orderId");
        String paymentKey = extractText(map, "paymentKey", "transactionKey", "key", "id");
        PgPaymentStatus status = parseStatus(extractText(map, "status", "paymentStatus", "result"));
        String reason = extractText(map, "reason", "message", "errorMessage", "failureReason");

        return new PgPaymentSnapshot(orderId, paymentKey, status, reason);
    }

    private PgPaymentStatus parseStatus(String raw) {
        if (raw == null || raw.isBlank()) {
            return PgPaymentStatus.UNKNOWN;
        }
        String normalized = raw.trim().toUpperCase();
        return switch (normalized) {
            case "REQUESTED", "PENDING" -> PgPaymentStatus.REQUESTED;
            case "PROCESSING", "IN_PROGRESS" -> PgPaymentStatus.PROCESSING;
            case "SUCCESS", "APPROVED", "PAID" -> PgPaymentStatus.SUCCESS;
            case "LIMIT_EXCEEDED", "OVER_LIMIT" -> PgPaymentStatus.LIMIT_EXCEEDED;
            case "INVALID_CARD", "WRONG_CARD" -> PgPaymentStatus.INVALID_CARD;
            case "FAILED", "FAIL" -> PgPaymentStatus.FAILED;
            default -> PgPaymentStatus.UNKNOWN;
        };
    }

    private String extractText(Map<?, ?> map, String... keys) {
        for (String key : keys) {
            Object value = map.get(key);
            if (value != null) {
                return String.valueOf(value);
            }
        }
        return null;
    }

    private Long extractLong(Map<?, ?> map, String... keys) {
        String text = extractText(map, keys);
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            return Long.valueOf(text);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private CoreException toExternalError(String message, Throwable t) {
        if (t instanceof CoreException coreException) {
            return coreException;
        }
        if (t instanceof RestClientException) {
            return new CoreException(ErrorType.INTERNAL_ERROR, message);
        }
        return new CoreException(ErrorType.INTERNAL_ERROR, message);
    }
}
