package com.loopers.infrastructure.client;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.contract.wiremock.AutoConfigureWireMock;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@AutoConfigureWireMock(port = 0)
@TestPropertySource(properties = "pg.simulator.url=http://localhost:${wiremock.server.port}")
class PgPaymentGatewayWireMockTest {

    @Autowired
    private PgPaymentGateway pgPaymentGateway;

    @DisplayName("결제 요청 시, ")
    @Nested
    class RequestPayment {

        @DisplayName("PG가 SUCCESS 응답을 내려주면 TransactionResponse가 반환된다.")
        @Test
        void returnsTransactionResponse_whenPgReturnsSuccess() {
            // arrange
            stubFor(post(urlEqualTo("/api/v1/payments"))
                    .withHeader("X-USER-ID", equalTo("user-1"))
                    .withRequestBody(matchingJsonPath("$.orderId", equalTo("pgOrderCode-001")))
                    .withRequestBody(matchingJsonPath("$.cardType", equalTo("SAMSUNG")))
                    .withRequestBody(matchingJsonPath("$.cardNo", equalTo("1234-5678-9012-3456")))
                    .withRequestBody(matchingJsonPath("$.amount", equalTo("10000")))
                    .withRequestBody(matchingJsonPath("$.callbackUrl", equalTo("http://callback")))
                    .willReturn(aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                            .withBody("""
                                    {
                                      "meta": { "result": "SUCCESS", "errorCode": null, "message": null },
                                      "data": { "transactionKey": "TXN-001", "status": "PENDING", "reason": null }
                                    }
                                    """)));

            PgPaymentDto.PaymentRequest request = new PgPaymentDto.PaymentRequest(
                    "pgOrderCode-001", "SAMSUNG", "1234-5678-9012-3456", 10000L, "http://callback");

            // act
            PgPaymentDto.TransactionResponse result = pgPaymentGateway.requestPayment("user-1", request);

            // assert
            assertThat(result.transactionKey()).isEqualTo("TXN-001");
        }

        @DisplayName("PG가 FAILED 응답을 내려주면 PgPaymentException이 발생한다.")
        @Test
        void throwsPgPaymentException_whenPgReturnsFailed() {
            // arrange
            stubFor(post(urlEqualTo("/api/v1/payments"))
                    .willReturn(aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                            .withBody("""
                                    {
                                      "meta": { "result": "FAILED", "errorCode": "CARD_LIMIT", "message": "한도초과" },
                                      "data": null
                                    }
                                    """)));

            PgPaymentDto.PaymentRequest request = new PgPaymentDto.PaymentRequest(
                    "pgOrderCode-002", "KB", "1234-5678-9012-3456", 10000L, "http://callback");

            // act & assert
            assertThatThrownBy(() -> pgPaymentGateway.requestPayment("user-1", request))
                    .isInstanceOf(PgPaymentException.class);
        }
    }

    @DisplayName("거래 조회 시, ")
    @Nested
    class GetTransaction {

        @DisplayName("PG가 SUCCESS 응답을 내려주면 TransactionDetailResponse가 반환된다.")
        @Test
        void returnsTransactionDetail_whenPgReturnsSuccess() {
            // arrange
            stubFor(get(urlEqualTo("/api/v1/payments/TXN-001"))
                    .withHeader("X-USER-ID", equalTo("user-1"))
                    .willReturn(aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                            .withBody("""
                                    {
                                      "meta": { "result": "SUCCESS", "errorCode": null, "message": null },
                                      "data": {
                                        "transactionKey": "TXN-001",
                                        "orderId": "pgOrderCode-001",
                                        "cardType": "SAMSUNG",
                                        "cardNo": "1234-5678-9012-3456",
                                        "amount": 10000,
                                        "status": "SUCCESS",
                                        "reason": null
                                      }
                                    }
                                    """)));

            // act
            PgPaymentDto.TransactionDetailResponse result =
                    pgPaymentGateway.getTransaction("user-1", "TXN-001");

            // assert
            assertThat(result.transactionKey()).isEqualTo("TXN-001");
            assertThat(result.status()).isEqualTo("SUCCESS");
        }

        @DisplayName("PG가 FAILED 응답을 내려주면 PgPaymentException이 발생한다.")
        @Test
        void throwsPgPaymentException_whenPgReturnsFailed() {
            // arrange
            stubFor(get(urlEqualTo("/api/v1/payments/TXN-002"))
                    .willReturn(aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                            .withBody("""
                                    {
                                      "meta": { "result": "FAILED", "errorCode": "NOT_FOUND", "message": "거래 없음" },
                                      "data": null
                                    }
                                    """)));

            // act & assert
            assertThatThrownBy(() -> pgPaymentGateway.getTransaction("user-1", "TXN-002"))
                    .isInstanceOf(PgPaymentException.class);
        }
    }

    @DisplayName("주문코드로 거래 조회 시, ")
    @Nested
    class GetTransactionsByOrder {

        @DisplayName("PG가 SUCCESS 응답을 내려주면 OrderTransactionResponse가 반환된다.")
        @Test
        void returnsOrderTransactionResponse_whenPgReturnsSuccess() {
            // arrange
            stubFor(get(urlEqualTo("/api/v1/payments?orderId=pgOrderCode-001"))
                    .withHeader("X-USER-ID", equalTo("user-1"))
                    .willReturn(aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                    .withBody("""
                            {
                              "meta": { "result": "SUCCESS", "errorCode": null, "message": null },
                              "data": {
                                "orderId": "pgOrderCode-001",
                                "transactions": [
                                  { "transactionKey": "TXN-001", "status": "SUCCESS", "reason": null }
                                ]
                              }
                            }
                            """)));

            // act
            PgPaymentDto.OrderTransactionResponse result =
                    pgPaymentGateway.getTransactionsByOrder("user-1", "pgOrderCode-001");

            // assert
            assertThat(result.orderId()).isEqualTo("pgOrderCode-001");
            assertThat(result.transactions()).hasSize(1);
            assertThat(result.transactions().get(0).transactionKey()).isEqualTo("TXN-001");
            assertThat(result.transactions().get(0).status()).isEqualTo("SUCCESS");
        }

        @DisplayName("PG가 FAILED 응답을 내려주면 PgPaymentException이 발생한다.")
        @Test
        void throwsPgPaymentException_whenPgReturnsFailed() {
            // arrange
            stubFor(get(urlEqualTo("/api/v1/payments?orderId=pgOrderCode-999"))
                    .willReturn(aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                            .withBody("""
                                    {
                                      "meta": { "result": "FAILED", "errorCode": "NOT_FOUND", "message": "주문 없음" },
                                      "data": null
                                    }
                                    """)));

            // act & assert
            assertThatThrownBy(() -> pgPaymentGateway.getTransactionsByOrder("user-1", "pgOrderCode-999"))
                    .isInstanceOf(PgPaymentException.class);
        }
    }
}
