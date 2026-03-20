package com.loopers.infrastructure.payment;

import com.loopers.domain.payment.GatewayPaymentResult;
import com.loopers.support.enums.CardType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.util.List;

/**
 * PG 시뮬레이터와의 순수 HTTP 통신을 담당한다.
 * <p>
 * Resilience 정책(Retry, CircuitBreaker)은 이 클래스의 관심사가 아니다.
 * {@link ResilientPgClient}가 이 클래스를 감싸서 resilience를 적용한다.
 * </p>
 * <p>
 * 예외 변환 없이 RestTemplate 원본 예외를 그대로 던진다.
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PgHttpClient {

    private final RestTemplate pgRestTemplate;

    @Value("${pg.base-url}")
    private String baseUrl;

    /**
     * PG에 결제를 요청한다. (순수 HTTP 호출)
     */
    public GatewayPaymentResult requestPayment(Long orderId, Long userId,
                                                CardType cardType, String cardNo,
                                                BigDecimal amount, String callbackUrl) {
        PgPaymentRequest pgRequest = PgPaymentRequest.from(orderId, cardType, cardNo, amount, callbackUrl);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-USER-ID", String.valueOf(userId));
        HttpEntity<PgPaymentRequest> entity = new HttpEntity<>(pgRequest, headers);

        ResponseEntity<PgApiResponse<PgPaymentResponse>> response = pgRestTemplate.exchange(
                baseUrl + "/api/v1/payments", HttpMethod.POST, entity,
                new ParameterizedTypeReference<>() {}
        );

        return response.getBody().data().toGatewayResult();
    }

    /**
     * transactionKey로 PG 결제 상태를 조회한다. (순수 HTTP 호출)
     */
    public GatewayPaymentResult getPaymentStatus(String transactionKey) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-USER-ID", "system");
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        ResponseEntity<PgApiResponse<PgPaymentResponse>> response = pgRestTemplate.exchange(
                baseUrl + "/api/v1/payments/" + transactionKey, HttpMethod.GET, entity,
                new ParameterizedTypeReference<>() {}
        );

        return response.getBody().data().toGatewayResult();
    }

    /**
     * orderId로 PG 결제 목록을 조회한다. (순수 HTTP 호출)
     */
    public List<GatewayPaymentResult> getPaymentsByOrderId(Long orderId) {
        String orderIdStr = String.format("%06d", orderId);

        HttpHeaders headers = new HttpHeaders();
        headers.set("X-USER-ID", "system");
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        try {
            ResponseEntity<PgApiResponse<PgOrderPaymentsResponse>> response = pgRestTemplate.exchange(
                    baseUrl + "/api/v1/payments?orderId=" + orderIdStr, HttpMethod.GET, entity,
                    new ParameterizedTypeReference<>() {}
            );
            return response.getBody().data().toGatewayResults();
        } catch (Exception e) {
            log.debug("PG orderId 조회 실패: orderId={}, error={}", orderId, e.getMessage());
            return List.of();
        }
    }
}
