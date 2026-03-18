package com.loopers.interfaces.api;

import com.loopers.domain.order.Order;
import com.loopers.domain.order.OrderItemSnapshot;
import com.loopers.domain.payment.CardType;
import com.loopers.domain.payment.Payment;
import com.loopers.domain.payment.PaymentStatus;
import com.loopers.domain.user.User;
import com.loopers.domain.user.UserFixture;
import com.loopers.infrastructure.client.PgPaymentDto;
import com.loopers.infrastructure.client.PgPaymentGateway;
import com.loopers.infrastructure.order.OrderJpaRepository;
import com.loopers.infrastructure.payment.PaymentJpaRepository;
import com.loopers.infrastructure.user.UserJpaRepository;
import com.loopers.interfaces.api.payment.PaymentV1Dto;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class PaymentV1ApiE2ETest {

    private static final String ENDPOINT = "/api/v1/payments";
    private static final String RAW_PASSWORD = "TestPass1!";

    @Autowired private TestRestTemplate testRestTemplate;
    @Autowired private UserJpaRepository userJpaRepository;
    @Autowired private OrderJpaRepository orderJpaRepository;
    @Autowired private PaymentJpaRepository paymentJpaRepository;
    @Autowired private DatabaseCleanUp databaseCleanUp;

    @MockitoBean
    private PgPaymentGateway pgPaymentGateway;

    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    private User savedUser;
    private Order savedOrder;

    @BeforeEach
    void setUp() {
        savedUser = userJpaRepository.save(
                UserFixture.builder()
                           .loginId("paymentTestUser")
                           .password(encoder.encode(RAW_PASSWORD))
                           .build()
        );
        savedOrder = orderJpaRepository.save(
                Order.create(savedUser.getId(), List.of(new OrderItemSnapshot(1L, "상품", 10000L, 1)))
        );
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    private HttpHeaders userHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Loopers-LoginId", savedUser.getLoginId());
        headers.set("X-Loopers-LoginPw", RAW_PASSWORD);
        return headers;
    }

    @DisplayName("결제 요청 시, ")
    @Nested
    class RequestPayment {

        @DisplayName("유효한 요청이면 202 Accepted와 결제 정보를 반환한다.")
        @Test
        void returnsAccepted_whenValidRequest() {
            // arrange
            given(pgPaymentGateway.requestPayment(anyString(), any()))
                    .willReturn(Optional.of(new PgPaymentDto.TransactionResponse("TXN-001", "PENDING", null)));

            PaymentV1Dto.PaymentRequest request = new PaymentV1Dto.PaymentRequest(savedOrder.getId(), CardType.SAMSUNG, "1234-5678-9012-3456");
            HttpEntity<PaymentV1Dto.PaymentRequest> entity = new HttpEntity<>(request, userHeaders());

            // act
            ResponseEntity<ApiResponse<PaymentV1Dto.PaymentResponse>> response =
                    testRestTemplate.exchange(ENDPOINT, HttpMethod.POST, entity, new ParameterizedTypeReference<>() {});

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
            assertThat(response.getBody().data().status()).isEqualTo(PaymentStatus.PENDING);
        }

        @DisplayName("인증 헤더 없이 요청하면 404를 반환한다.")
        @Test
        void returnsNotFound_whenNoAuthHeader() {
            // arrange
            PaymentV1Dto.PaymentRequest request = new PaymentV1Dto.PaymentRequest(savedOrder.getId(), CardType.SAMSUNG, "1234-5678-9012-3456");

            // act
            ResponseEntity<Void> response =
                    testRestTemplate.exchange(ENDPOINT, HttpMethod.POST, new HttpEntity<>(request), new ParameterizedTypeReference<>() {});

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }
    }

    @DisplayName("PG 콜백 수신 시, ")
    @Nested
    class Callback {

        @DisplayName("SUCCESS 콜백이면 200 OK 응답과 함께 Payment/Order 상태가 전환된다.")
        @Test
        void completesPaymentAndOrder_whenSuccessCallbackWithoutAuth() {
            // arrange
            Payment payment = paymentJpaRepository.save(
                    Payment.create(savedOrder.getId(), "pgOrderCode-001", CardType.SAMSUNG, "1234-5678-9012-3456", 10000L)
            );
            payment.assignPgTransaction("TXN-001");
            paymentJpaRepository.save(payment);

            PaymentV1Dto.CallbackRequest request = new PaymentV1Dto.CallbackRequest(
                    "TXN-001", "pgOrderCode-001", "SAMSUNG", "1234-5678-9012-3456", 10000L, "SUCCESS", null
            );

            // act (헤더 없이 요청 - PG 서버는 인증 없이 콜백 전송)
            ResponseEntity<ApiResponse<Void>> response =
                    testRestTemplate.exchange(ENDPOINT + "/callback", HttpMethod.POST, new HttpEntity<>(request), new ParameterizedTypeReference<>() {});

            // assert
            Payment updatedPayment = paymentJpaRepository.findById(payment.getId()).orElseThrow();
            Order updatedOrder = orderJpaRepository.findById(savedOrder.getId()).orElseThrow();
            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(updatedPayment.getStatus()).isEqualTo(PaymentStatus.COMPLETED),
                    () -> assertThat(updatedOrder.getStatus()).isEqualTo(Order.Status.PAID)
            );
        }
    }

    @DisplayName("결제 상태 동기화 시, ")
    @Nested
    class SyncPayment {

        @DisplayName("유효한 요청이면 200 OK와 최신 결제 상태를 반환한다.")
        @Test
        void returnsOk_whenValidRequest() {
            // arrange
            Payment payment = paymentJpaRepository.save(
                    Payment.create(savedOrder.getId(), "pgOrderCode-002", CardType.KB, "1234-5678-9012-3456", 10000L)
            );
            payment.assignPgTransaction("TXN-002");
            paymentJpaRepository.save(payment);

            given(pgPaymentGateway.getTransaction(anyString(), anyString()))
                    .willReturn(Optional.of(new PgPaymentDto.TransactionDetailResponse("TXN-002", "pgOrderCode-002", "KB", "1234-5678-9012-3456", 10000L, "SUCCESS", null)));

            HttpEntity<Void> entity = new HttpEntity<>(userHeaders());

            // act
            ResponseEntity<ApiResponse<PaymentV1Dto.PaymentResponse>> response =
                    testRestTemplate.exchange(ENDPOINT + "/" + payment.getId() + "/sync", HttpMethod.POST, entity, new ParameterizedTypeReference<>() {});

            // assert
            Order updatedOrder = orderJpaRepository.findById(savedOrder.getId()).orElseThrow();
            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(response.getBody().data().status()).isEqualTo(PaymentStatus.COMPLETED),
                    () -> assertThat(updatedOrder.getStatus()).isEqualTo(Order.Status.PAID)
            );
        }

        @DisplayName("인증 헤더 없이 요청하면 404를 반환한다.")
        @Test
        void returnsNotFound_whenNoAuthHeader() {
            // act
            ResponseEntity<Void> response =
                    testRestTemplate.exchange(ENDPOINT + "/999/sync", HttpMethod.POST, new HttpEntity<>(null), new ParameterizedTypeReference<>() {});

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }
    }
}
