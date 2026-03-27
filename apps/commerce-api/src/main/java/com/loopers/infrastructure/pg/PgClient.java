package com.loopers.infrastructure.pg;

import com.loopers.infrastructure.pg.dto.PgPaymentRequest;
import com.loopers.infrastructure.pg.dto.PgPaymentResponse;
import com.loopers.infrastructure.pg.exception.PgClientException;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.util.Optional;

@Component
public class PgClient {
    private static final String USER_ID_HEADER = "X-USER-ID";

    private final RestClient pgRestClient;

    public PgClient(RestClient pgRestClient) {
        this.pgRestClient = pgRestClient;
    }

    public PgPaymentResponse requestPayment(Long userId, PgPaymentRequest request) {
        try {
            return pgRestClient.post()
                    .uri("/api/v1/payments")
                    .header(USER_ID_HEADER, String.valueOf(userId))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .onStatus(HttpStatusCode::is4xxClientError, (req, res) -> {
                        throw PgClientException.clientError(res.getStatusCode().value(),
                                "결제 요청 실패: " + res.getStatusCode());
                    })
                    .onStatus(HttpStatusCode::is5xxServerError, (req, res) -> {
                        throw PgClientException.serverError(res.getStatusCode().value(),
                                "PG 서버 오류: " + res.getStatusCode());
                    })
                    .body(PgPaymentResponse.class);
        } catch (ResourceAccessException e) {
            throw PgClientException.connectionError(e);
        } catch (PgClientException e) {
            throw e;
        } catch (Exception e) {
            throw PgClientException.parseError(e);
        }
    }

    public Optional<PgPaymentResponse> getPayment(Long userId, String transactionId) {
        try {
            PgPaymentResponse response = pgRestClient.get()
                    .uri("/api/v1/payments/{transactionId}", transactionId)
                    .header(USER_ID_HEADER, String.valueOf(userId))
                    .retrieve()
                    .onStatus(status -> status.value() == 404, (req, res) -> {
                        // 404는 Optional.empty()로 처리
                    })
                    .onStatus(HttpStatusCode::is4xxClientError, (req, res) -> {
                        if (res.getStatusCode().value() != 404) {
                            throw PgClientException.clientError(res.getStatusCode().value(),
                                    "결제 조회 실패: " + res.getStatusCode());
                        }
                    })
                    .onStatus(HttpStatusCode::is5xxServerError, (req, res) -> {
                        throw PgClientException.serverError(res.getStatusCode().value(),
                                "PG 서버 오류: " + res.getStatusCode());
                    })
                    .body(PgPaymentResponse.class);

            return Optional.ofNullable(response);
        } catch (ResourceAccessException e) {
            throw PgClientException.connectionError(e);
        } catch (PgClientException e) {
            throw e;
        } catch (Exception e) {
            throw PgClientException.parseError(e);
        }
    }

    public Optional<PgPaymentResponse> getPaymentByOrderId(Long userId, String orderId) {
        try {
            PgPaymentResponse response = pgRestClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/api/v1/payments")
                            .queryParam("orderId", orderId)
                            .build())
                    .header(USER_ID_HEADER, String.valueOf(userId))
                    .retrieve()
                    .onStatus(status -> status.value() == 404, (req, res) -> {
                        // 404는 Optional.empty()로 처리
                    })
                    .onStatus(HttpStatusCode::is4xxClientError, (req, res) -> {
                        if (res.getStatusCode().value() != 404) {
                            throw PgClientException.clientError(res.getStatusCode().value(),
                                    "결제 조회 실패: " + res.getStatusCode());
                        }
                    })
                    .onStatus(HttpStatusCode::is5xxServerError, (req, res) -> {
                        throw PgClientException.serverError(res.getStatusCode().value(),
                                "PG 서버 오류: " + res.getStatusCode());
                    })
                    .body(PgPaymentResponse.class);

            return Optional.ofNullable(response);
        } catch (ResourceAccessException e) {
            throw PgClientException.connectionError(e);
        } catch (PgClientException e) {
            throw e;
        } catch (Exception e) {
            throw PgClientException.parseError(e);
        }
    }
}
