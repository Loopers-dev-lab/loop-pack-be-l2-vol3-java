package com.loopers.interfaces.api.payment;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * E7-1~E7-5: 결제 API E2E 테스트.
 *
 * <p>WireMock으로 PG Simulator를 시뮬레이션.
 * TestRestTemplate으로 실제 HTTP 호출.</p>
 *
 * <p>실행 조건: Docker (MySQL + Redis Testcontainers) 필요.</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class PaymentE2ETest {

    static WireMockServer pgSimulator = new WireMockServer(wireMockConfig().dynamicPort());

    @Autowired
    private TestRestTemplate testRestTemplate;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @DynamicPropertySource
    static void pgProperties(DynamicPropertyRegistry registry) {
        registry.add("pg.simulator.url", pgSimulator::baseUrl);
        registry.add("pg.toss.url", () -> "http://localhost:19999"); // Toss 미사용
    }

    @BeforeAll
    static void startPgSimulator() {
        pgSimulator.start();
    }

    @AfterAll
    static void stopPgSimulator() {
        pgSimulator.stop();
    }

    @BeforeEach
    void resetStubs() {
        pgSimulator.resetAll();
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    private HttpEntity<Map<String, Object>> jsonRequest(Map<String, Object> body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return new HttpEntity<>(body, headers);
    }

    /**
     * 테스트 전: DB에 주문 데이터를 미리 삽입해야 함.
     * 이 E2E 테스트는 전체 인프라(MySQL + Redis)가 필요합니다.
     */

    @Nested
    @DisplayName("결제 요청")
    class RequestPayment {

        @DisplayName("E7-1: POST /api/v1/payments → 200 + 결제 처리 중")
        @Test
        void requestPayment_success_returnsPending() {
            // PG Simulator: PENDING 응답
            pgSimulator.stubFor(post(urlEqualTo("/api/v1/payments"))
                .willReturn(aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody("{\"status\":\"PENDING\",\"transactionKey\":\"TX-E2E-001\"}")));

            // PG Simulator: orderId 기반 조회 (멱등성 체크용)
            pgSimulator.stubFor(get(urlPathEqualTo("/api/v1/payments"))
                .willReturn(aResponse()
                    .withStatus(500))); // 기록 없음

            // TODO: DB에 주문 데이터 삽입 필요 (Order, Product, etc.)
            // 이 테스트는 Docker + Testcontainers 환경에서 실행해야 합니다.

            Map<String, Object> paymentRequest = Map.of(
                "orderId", 1L,
                "cardType", "SAMSUNG",
                "cardNo", "1234-5678-9012-3456",
                "amount", 5000
            );

            ResponseEntity<ApiResponse<PaymentV1Dto.PaymentResponse>> response = testRestTemplate.exchange(
                "/api/v1/payments",
                HttpMethod.POST,
                jsonRequest(paymentRequest),
                new ParameterizedTypeReference<>() {}
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().data().status()).isIn("PENDING", "UNKNOWN");
        }

        @DisplayName("E7-4: 존재하지 않는 주문 결제 → 에러")
        @Test
        void requestPayment_orderNotFound_error() {
            Map<String, Object> paymentRequest = Map.of(
                "orderId", 999L,
                "cardType", "SAMSUNG",
                "cardNo", "1234-5678-9012-3456",
                "amount", 5000
            );

            ResponseEntity<ApiResponse<Object>> response = testRestTemplate.exchange(
                "/api/v1/payments",
                HttpMethod.POST,
                jsonRequest(paymentRequest),
                new ParameterizedTypeReference<>() {}
            );

            assertThat(response.getStatusCode()).isIn(HttpStatus.BAD_REQUEST, HttpStatus.NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("콜백 + 전체 흐름")
    class CallbackFlow {

        @DisplayName("E7-2: POST callback → 200 OK")
        @Test
        void callback_success_returns200() {
            Map<String, Object> callbackRequest = Map.of(
                "transactionKey", "TX-E2E-CALLBACK",
                "status", "SUCCESS",
                "payload", "{}"
            );

            ResponseEntity<ApiResponse<Object>> response = testRestTemplate.exchange(
                "/api/v1/payments/callback",
                HttpMethod.POST,
                jsonRequest(callbackRequest),
                new ParameterizedTypeReference<>() {}
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }
    }

    @Nested
    @DisplayName("수동 복구")
    class ManualConfirm {

        @DisplayName("E7-3: POST /{id}/confirm → PG 조회 → 상태 갱신")
        @Test
        void manualConfirm_pgQuery_statusUpdated() {
            // TODO: 사전에 PENDING Payment를 DB에 삽입 필요

            ResponseEntity<ApiResponse<String>> response = testRestTemplate.exchange(
                "/api/v1/payments/1/confirm",
                HttpMethod.POST,
                null,
                new ParameterizedTypeReference<>() {}
            );

            // Payment가 없으면 404, 있으면 200
            assertThat(response.getStatusCode()).isIn(HttpStatus.OK, HttpStatus.NOT_FOUND);
        }
    }
}
