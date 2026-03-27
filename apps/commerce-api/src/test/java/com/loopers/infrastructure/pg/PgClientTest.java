package com.loopers.infrastructure.pg;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.loopers.infrastructure.pg.dto.PgPaymentRequest;
import com.loopers.infrastructure.pg.dto.PgPaymentResponse;
import com.loopers.infrastructure.pg.dto.PgPaymentStatus;
import com.loopers.infrastructure.pg.exception.PgClientException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.ZonedDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

@DisplayName("PgClient 테스트")
class PgClientTest {

    private MockRestServiceServer mockServer;
    private PgClient pgClient;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());

        RestClient.Builder builder = RestClient.builder()
                .baseUrl("http://localhost:8081");

        mockServer = MockRestServiceServer.bindTo(builder).build();
        RestClient restClient = builder.build();
        pgClient = new PgClient(restClient);
    }

    @Nested
    @DisplayName("requestPayment 테스트")
    class RequestPayment {

        @Test
        @DisplayName("성공 - 결제 요청")
        void 결제_요청_성공() throws Exception {
            // Arrange
            Long userId = 1L;
            PgPaymentRequest request = new PgPaymentRequest(
                    "ORDER-001",
                    "VISA",
                    "4111111111111111",
                    50000L,
                    "http://callback.url"
            );

            PgPaymentResponse expectedResponse = new PgPaymentResponse(
                    "TXN-001",
                    "ORDER-001",
                    PgPaymentStatus.APPROVED,
                    50000L,
                    "VISA",
                    "4111111111111111",
                    ZonedDateTime.now(),
                    ZonedDateTime.now(),
                    null
            );

            mockServer.expect(requestTo("http://localhost:8081/api/v1/payments"))
                    .andExpect(method(HttpMethod.POST))
                    .andExpect(header("X-USER-ID", "1"))
                    .andRespond(withSuccess(objectMapper.writeValueAsString(expectedResponse), MediaType.APPLICATION_JSON));

            // Act
            PgPaymentResponse response = pgClient.requestPayment(userId, request);

            // Assert
            assertThat(response).isNotNull();
            assertThat(response.transactionId()).isEqualTo("TXN-001");
            assertThat(response.orderId()).isEqualTo("ORDER-001");
            assertThat(response.status()).isEqualTo(PgPaymentStatus.APPROVED);
            assertThat(response.isSuccess()).isTrue();

            mockServer.verify();
        }

        @Test
        @DisplayName("실패 - 4xx 클라이언트 에러")
        void 클라이언트_에러() {
            // Arrange
            Long userId = 1L;
            PgPaymentRequest request = new PgPaymentRequest(
                    "ORDER-001",
                    "VISA",
                    "4111111111111111",
                    50000L,
                    "http://callback.url"
            );

            mockServer.expect(requestTo("http://localhost:8081/api/v1/payments"))
                    .andExpect(method(HttpMethod.POST))
                    .andRespond(withStatus(HttpStatus.BAD_REQUEST));

            // Act & Assert
            assertThatThrownBy(() -> pgClient.requestPayment(userId, request))
                    .isInstanceOf(PgClientException.class)
                    .satisfies(ex -> {
                        PgClientException pgEx = (PgClientException) ex;
                        assertThat(pgEx.getHttpStatusCode()).isEqualTo(400);
                        assertThat(pgEx.isRetryable()).isFalse();
                    });

            mockServer.verify();
        }

        @Test
        @DisplayName("실패 - 5xx 서버 에러")
        void 서버_에러() {
            // Arrange
            Long userId = 1L;
            PgPaymentRequest request = new PgPaymentRequest(
                    "ORDER-001",
                    "VISA",
                    "4111111111111111",
                    50000L,
                    "http://callback.url"
            );

            mockServer.expect(requestTo("http://localhost:8081/api/v1/payments"))
                    .andExpect(method(HttpMethod.POST))
                    .andRespond(withServerError());

            // Act & Assert
            assertThatThrownBy(() -> pgClient.requestPayment(userId, request))
                    .isInstanceOf(PgClientException.class)
                    .satisfies(ex -> {
                        PgClientException pgEx = (PgClientException) ex;
                        assertThat(pgEx.getHttpStatusCode()).isEqualTo(500);
                        assertThat(pgEx.isRetryable()).isTrue();
                    });

            mockServer.verify();
        }
    }

    @Nested
    @DisplayName("getPayment 테스트")
    class GetPayment {

        @Test
        @DisplayName("성공 - 결제 조회")
        void 결제_조회_성공() throws Exception {
            // Arrange
            Long userId = 1L;
            String transactionId = "TXN-001";

            PgPaymentResponse expectedResponse = new PgPaymentResponse(
                    transactionId,
                    "ORDER-001",
                    PgPaymentStatus.APPROVED,
                    50000L,
                    "VISA",
                    "4111111111111111",
                    ZonedDateTime.now(),
                    ZonedDateTime.now(),
                    null
            );

            mockServer.expect(requestTo("http://localhost:8081/api/v1/payments/TXN-001"))
                    .andExpect(method(HttpMethod.GET))
                    .andExpect(header("X-USER-ID", "1"))
                    .andRespond(withSuccess(objectMapper.writeValueAsString(expectedResponse), MediaType.APPLICATION_JSON));

            // Act
            Optional<PgPaymentResponse> response = pgClient.getPayment(userId, transactionId);

            // Assert
            assertThat(response).isPresent();
            assertThat(response.get().transactionId()).isEqualTo(transactionId);

            mockServer.verify();
        }

        @Test
        @DisplayName("성공 - 결제 없음 (404)")
        void 결제_없음_404() {
            // Arrange
            Long userId = 1L;
            String transactionId = "TXN-NOTFOUND";

            mockServer.expect(requestTo("http://localhost:8081/api/v1/payments/TXN-NOTFOUND"))
                    .andExpect(method(HttpMethod.GET))
                    .andRespond(withStatus(HttpStatus.NOT_FOUND));

            // Act
            Optional<PgPaymentResponse> response = pgClient.getPayment(userId, transactionId);

            // Assert
            assertThat(response).isEmpty();

            mockServer.verify();
        }
    }

    @Nested
    @DisplayName("getPaymentByOrderId 테스트")
    class GetPaymentByOrderId {

        @Test
        @DisplayName("성공 - 주문 ID로 결제 조회")
        void 주문ID로_결제_조회_성공() throws Exception {
            // Arrange
            Long userId = 1L;
            String orderId = "ORDER-001";

            PgPaymentResponse expectedResponse = new PgPaymentResponse(
                    "TXN-001",
                    orderId,
                    PgPaymentStatus.APPROVED,
                    50000L,
                    "VISA",
                    "4111111111111111",
                    ZonedDateTime.now(),
                    ZonedDateTime.now(),
                    null
            );

            mockServer.expect(requestTo("http://localhost:8081/api/v1/payments?orderId=ORDER-001"))
                    .andExpect(method(HttpMethod.GET))
                    .andExpect(header("X-USER-ID", "1"))
                    .andRespond(withSuccess(objectMapper.writeValueAsString(expectedResponse), MediaType.APPLICATION_JSON));

            // Act
            Optional<PgPaymentResponse> response = pgClient.getPaymentByOrderId(userId, orderId);

            // Assert
            assertThat(response).isPresent();
            assertThat(response.get().orderId()).isEqualTo(orderId);

            mockServer.verify();
        }
    }

    @Nested
    @DisplayName("PgPaymentResponse 헬퍼 메서드 테스트")
    class ResponseHelpers {

        @Test
        @DisplayName("isSuccess - APPROVED 상태")
        void isSuccess_APPROVED() {
            // Arrange
            PgPaymentResponse response = new PgPaymentResponse(
                    "TXN-001", "ORDER-001", PgPaymentStatus.APPROVED,
                    50000L, "VISA", "4111111111111111",
                    ZonedDateTime.now(), ZonedDateTime.now(), null
            );

            // Act & Assert
            assertThat(response.isSuccess()).isTrue();
            assertThat(response.isPending()).isFalse();
            assertThat(response.isFailed()).isFalse();
        }

        @Test
        @DisplayName("isPending - PENDING 상태")
        void isPending_PENDING() {
            // Arrange
            PgPaymentResponse response = new PgPaymentResponse(
                    "TXN-001", "ORDER-001", PgPaymentStatus.PENDING,
                    50000L, "VISA", "4111111111111111",
                    ZonedDateTime.now(), ZonedDateTime.now(), null
            );

            // Act & Assert
            assertThat(response.isSuccess()).isFalse();
            assertThat(response.isPending()).isTrue();
            assertThat(response.isFailed()).isFalse();
        }

        @Test
        @DisplayName("isFailed - FAILED 상태")
        void isFailed_FAILED() {
            // Arrange
            PgPaymentResponse response = new PgPaymentResponse(
                    "TXN-001", "ORDER-001", PgPaymentStatus.FAILED,
                    50000L, "VISA", "4111111111111111",
                    ZonedDateTime.now(), ZonedDateTime.now(), "잔액 부족"
            );

            // Act & Assert
            assertThat(response.isSuccess()).isFalse();
            assertThat(response.isPending()).isFalse();
            assertThat(response.isFailed()).isTrue();
        }

        @Test
        @DisplayName("isFailed - REJECTED 상태")
        void isFailed_REJECTED() {
            // Arrange
            PgPaymentResponse response = new PgPaymentResponse(
                    "TXN-001", "ORDER-001", PgPaymentStatus.REJECTED,
                    50000L, "VISA", "4111111111111111",
                    ZonedDateTime.now(), ZonedDateTime.now(), "카드 거절"
            );

            // Act & Assert
            assertThat(response.isSuccess()).isFalse();
            assertThat(response.isPending()).isFalse();
            assertThat(response.isFailed()).isTrue();
        }
    }
}
