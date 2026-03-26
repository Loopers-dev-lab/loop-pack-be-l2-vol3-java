package com.loopers.infrastructure.payment;

import com.loopers.domain.payment.GatewayPaymentResult;
import com.loopers.support.enums.CardType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

@DisplayName("PgHttpClient — 순수 HTTP 통신 테스트")
class PgHttpClientTest {

    private static final String BASE_URL = "http://localhost:8081";

    private RestTemplate restTemplate;
    private MockRestServiceServer mockServer;
    private PgHttpClient pgHttpClient;

    @BeforeEach
    void setUp() {
        restTemplate = new RestTemplate();
        mockServer = MockRestServiceServer.createServer(restTemplate);
        pgHttpClient = new PgHttpClient(restTemplate);
        ReflectionTestUtils.setField(pgHttpClient, "baseUrl", BASE_URL);
    }

    @Test
    @DisplayName("결제 요청 성공 시 GatewayPaymentResult를 반환한다")
    void requestPayment_WithSuccessResponse_ShouldReturnResult() {
        mockServer.expect(requestTo(BASE_URL + "/api/v1/payments"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-USER-ID", "1"))
                .andExpect(header("Content-Type", "application/json"))
                .andRespond(withSuccess("""
                        {
                            "data": {
                                "transactionKey": "txn-001",
                                "success": true,
                                "status": "SUCCESS",
                                "reason": null
                            }
                        }
                        """, MediaType.APPLICATION_JSON));

        GatewayPaymentResult result = pgHttpClient.requestPayment(
                100L, 1L, CardType.SAMSUNG, "1234-5678-9012-3456",
                BigDecimal.valueOf(50000), "http://localhost:8080/callback"
        );

        assertThat(result.transactionKey()).isEqualTo("txn-001");
        assertThat(result.success()).isTrue();
        assertThat(result.status()).isEqualTo("SUCCESS");
        mockServer.verify();
    }

    @Test
    @DisplayName("결제 요청 시 PENDING 응답을 처리한다")
    void requestPayment_WithPendingResponse_ShouldReturnPendingResult() {
        mockServer.expect(requestTo(BASE_URL + "/api/v1/payments"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("""
                        {
                            "data": {
                                "transactionKey": "txn-002",
                                "success": false,
                                "status": "PENDING",
                                "reason": null
                            }
                        }
                        """, MediaType.APPLICATION_JSON));

        GatewayPaymentResult result = pgHttpClient.requestPayment(
                101L, 2L, CardType.KB, "1111-2222-3333-4444",
                BigDecimal.valueOf(30000), "http://localhost:8080/callback"
        );

        assertThat(result.isPending()).isTrue();
        assertThat(result.isCompleted()).isFalse();
        mockServer.verify();
    }

    @Test
    @DisplayName("결제 상태 조회 성공 시 GatewayPaymentResult를 반환한다")
    void getPaymentStatus_WithSuccessResponse_ShouldReturnResult() {
        mockServer.expect(requestTo(BASE_URL + "/api/v1/payments/txn-001"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("X-USER-ID", "system"))
                .andRespond(withSuccess("""
                        {
                            "data": {
                                "transactionKey": "txn-001",
                                "success": true,
                                "status": "SUCCESS",
                                "reason": null
                            }
                        }
                        """, MediaType.APPLICATION_JSON));

        GatewayPaymentResult result = pgHttpClient.getPaymentStatus("txn-001");

        assertThat(result.transactionKey()).isEqualTo("txn-001");
        assertThat(result.success()).isTrue();
        assertThat(result.isCompleted()).isTrue();
        mockServer.verify();
    }

    @Test
    @DisplayName("결제 상태 조회 시 FAILED 응답을 처리한다")
    void getPaymentStatus_WithFailedResponse_ShouldReturnFailedResult() {
        mockServer.expect(requestTo(BASE_URL + "/api/v1/payments/txn-003"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        {
                            "data": {
                                "transactionKey": "txn-003",
                                "success": false,
                                "status": "FAILED",
                                "reason": "잔액 부족"
                            }
                        }
                        """, MediaType.APPLICATION_JSON));

        GatewayPaymentResult result = pgHttpClient.getPaymentStatus("txn-003");

        assertThat(result.success()).isFalse();
        assertThat(result.status()).isEqualTo("FAILED");
        assertThat(result.reason()).isEqualTo("잔액 부족");
        mockServer.verify();
    }

    @Test
    @DisplayName("PG 서버 에러(500) 시 예외가 발생한다")
    void requestPayment_WithServerError_ShouldThrowException() {
        mockServer.expect(requestTo(BASE_URL + "/api/v1/payments"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withServerError());

        assertThatThrownBy(() -> pgHttpClient.requestPayment(
                102L, 3L, CardType.HYUNDAI, "5555-6666-7777-8888",
                BigDecimal.valueOf(20000), "http://localhost:8080/callback"
        )).isInstanceOf(Exception.class);

        mockServer.verify();
    }
}
