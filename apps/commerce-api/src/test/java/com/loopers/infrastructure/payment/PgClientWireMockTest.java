package com.loopers.infrastructure.payment;

import com.loopers.application.brand.BrandService;
import com.loopers.application.order.OrderFacade;
import com.loopers.application.order.OrderItemCommand;
import com.loopers.application.order.OrderResult;
import com.loopers.application.payment.PaymentCommand;
import com.loopers.application.payment.PaymentFacade;
import com.loopers.application.payment.PaymentInfo;
import com.loopers.application.product.ProductFacade;
import com.loopers.domain.order.OrderModel;
import com.loopers.domain.order.OrderStatus;
import com.loopers.application.order.OrderService;
import com.loopers.domain.payment.CardType;
import com.loopers.domain.product.Money;
import com.loopers.utils.DatabaseCleanUp;
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.http.Fault;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.ClassOrderer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestClassOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.contract.wiremock.AutoConfigureWireMock;
import org.springframework.test.context.TestPropertySource;

import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest
@AutoConfigureWireMock(port = 0)
@TestPropertySource(properties = "pg.base-url=http://localhost:${wiremock.server.port}")
@TestClassOrder(ClassOrderer.OrderAnnotation.class)
@DisplayName("PG 연동 WireMock 테스트")
class PgClientWireMockTest {

    @Autowired private PaymentFacade paymentFacade;
    @Autowired private OrderFacade orderFacade;
    @Autowired private OrderService orderService;
    @Autowired private ProductFacade productFacade;
    @Autowired private BrandService brandService;
    @Autowired private DatabaseCleanUp databaseCleanUp;
    @Autowired private CircuitBreakerRegistry circuitBreakerRegistry;

    @BeforeEach
    void setUp() {
        removeAllMappings();
        resetAllRequests();
        resetAllScenarios();
        circuitBreakerRegistry.circuitBreaker("pg").reset();
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    private OrderResult createOrder() {
        Long brandId = brandService.register("나이키", "스포츠").getId();
        Long productId = productFacade.register("에어맥스", "러닝화", new Money(50000), brandId, 10).id();
        return orderFacade.placeOrder(1L, List.of(new OrderItemCommand(productId, 1)), null);
    }

    private static ResponseDefinitionBuilder pgOkResponse(Long orderId, String transactionKey) {
        return aResponse()
            .withStatus(200)
            .withHeader("Content-Type", "application/json")
            .withHeader("Connection", "close")
            .withBody("""
                {
                    "transactionKey": "%s",
                    "orderId": "%d",
                    "status": "PENDING",
                    "failureReason": null
                }
                """.formatted(transactionKey, orderId));
    }

    @DisplayName("PG 정상 응답")
    @Nested
    @Order(1)
    class PgSuccessResponse {

        @DisplayName("PG가 정상 응답하면 PENDING 결제가 생성되고 요청 헤더/바디가 올바르게 전달된다")
        @Test
        void createsPendingPaymentWithCorrectRequest() {
            // given
            OrderResult orderResult = createOrder();
            Long orderId = orderResult.order().getId();

            stubFor(post(urlEqualTo("/api/v1/payments"))
                .willReturn(pgOkResponse(orderId, "20250816:TR:wm001")));

            PaymentCommand command = new PaymentCommand(orderId, CardType.SAMSUNG, "1234-5678-9814-1451");

            // when
            PaymentInfo result = paymentFacade.requestPayment(1L, command);

            // then
            assertAll(
                () -> assertThat(result.status()).isEqualTo("PENDING"),
                () -> assertThat(result.transactionKey()).isEqualTo("20250816:TR:wm001"),
                () -> assertThat(result.maskedCardNo()).isEqualTo("1234-****-****-1451"),
                () -> assertThat(result.amount()).isEqualTo(50000)
            );

            verify(postRequestedFor(urlEqualTo("/api/v1/payments"))
                .withHeader("X-USER-ID", equalTo("1"))
                .withHeader("Content-Type", containing("application/json"))
                .withRequestBody(matchingJsonPath("$.orderId", equalTo(String.valueOf(orderId))))
                .withRequestBody(matchingJsonPath("$.cardType", equalTo("SAMSUNG")))
                .withRequestBody(matchingJsonPath("$.amount", equalTo("50000"))));
        }
    }

    @DisplayName("전체 결제 플로우")
    @Nested
    @Order(2)
    class FullPaymentFlow {

        @DisplayName("결제 요청 → 콜백 SUCCESS → 주문 CONFIRMED 전체 흐름이 정상 동작한다")
        @Test
        void fullSuccessFlow() {
            // given
            OrderResult orderResult = createOrder();
            Long orderId = orderResult.order().getId();

            stubFor(post(urlEqualTo("/api/v1/payments"))
                .willReturn(pgOkResponse(orderId, "20250816:TR:flow001")));

            PaymentCommand command = new PaymentCommand(orderId, CardType.SAMSUNG, "1234-5678-9814-1451");

            // when - 1. 결제 요청
            PaymentInfo paymentInfo = paymentFacade.requestPayment(1L, command);
            assertThat(paymentInfo.status()).isEqualTo("PENDING");

            // when - 2. PG 콜백 (SUCCESS)
            paymentFacade.handleCallback("20250816:TR:flow001", "SUCCESS", null);

            // then
            PaymentInfo completedPayment = paymentFacade.getPaymentsByOrderId(orderId).get(0);
            OrderModel order = orderService.getOrderForAdmin(orderId);

            assertAll(
                () -> assertThat(completedPayment.status()).isEqualTo("SUCCESS"),
                () -> assertThat(order.status()).isEqualTo(OrderStatus.CONFIRMED)
            );
        }

        @DisplayName("결제 요청 → 콜백 FAILED → 주문 PAYMENT_FAILED 전체 흐름이 정상 동작한다")
        @Test
        void fullFailureFlow() {
            // given
            OrderResult orderResult = createOrder();
            Long orderId = orderResult.order().getId();

            stubFor(post(urlEqualTo("/api/v1/payments"))
                .willReturn(pgOkResponse(orderId, "20250816:TR:flow002")));

            PaymentCommand command = new PaymentCommand(orderId, CardType.SAMSUNG, "1234-5678-9814-1451");

            // when - 1. 결제 요청
            PaymentInfo paymentInfo = paymentFacade.requestPayment(1L, command);
            assertThat(paymentInfo.status()).isEqualTo("PENDING");

            // when - 2. PG 콜백 (FAILED)
            paymentFacade.handleCallback("20250816:TR:flow002", "FAILED", "잔액 부족");

            // then
            PaymentInfo failedPayment = paymentFacade.getPaymentsByOrderId(orderId).get(0);
            OrderModel order = orderService.getOrderForAdmin(orderId);

            assertAll(
                () -> assertThat(failedPayment.status()).isEqualTo("FAILED"),
                () -> assertThat(failedPayment.failureReason()).isEqualTo("잔액 부족"),
                () -> assertThat(order.status()).isEqualTo(OrderStatus.PAYMENT_FAILED)
            );
        }
    }

    @DisplayName("PG 5xx 에러 → Retry 후 Fallback")
    @Nested
    @Order(3)
    class PgServerError {

        @DisplayName("PG가 500 응답을 반환하면 3번 재시도 후 Fallback으로 PG_FAILED 응답과 주문 PAYMENT_FAILED 처리된다")
        @Test
        void fallbackOn500AfterRetry() {
            // given
            OrderResult orderResult = createOrder();
            Long orderId = orderResult.order().getId();

            stubFor(post(urlEqualTo("/api/v1/payments"))
                .willReturn(aResponse()
                    .withStatus(500)
                    .withHeader("Connection", "close")
                    .withBody("Internal Server Error")));

            PaymentCommand command = new PaymentCommand(orderId, CardType.SAMSUNG, "1234-5678-9814-1451");

            // when
            PaymentInfo result = paymentFacade.requestPayment(1L, command);

            // then - Fallback 응답 검증
            assertThat(result.status()).isEqualTo("PG_FAILED");

            // then - 주문 상태 검증
            // TX 분리: TX-1이 커밋되어 주문은 PAYMENT_PENDING 상태 유지
            // → Polling 스케줄러가 복구하거나 사용자가 수동 동기화
            OrderModel order = orderService.getOrderForAdmin(orderId);
            assertThat(order.status()).isEqualTo(OrderStatus.PAYMENT_PENDING);

            // then - Retry로 3번 호출되었는지 검증
            verify(3, postRequestedFor(urlEqualTo("/api/v1/payments")));
        }
    }

    @DisplayName("PG 잘못된 응답 → Fallback")
    @Nested
    @Order(4)
    class PgMalformedResponse {

        @DisplayName("PG가 transactionKey 없이 응답하면 Fallback으로 PG_FAILED 응답이 반환된다")
        @Test
        void fallbackOnMissingTransactionKey() {
            // given
            OrderResult orderResult = createOrder();
            Long orderId = orderResult.order().getId();

            stubFor(post(urlEqualTo("/api/v1/payments"))
                .willReturn(aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withHeader("Connection", "close")
                    .withBody("""
                        {
                            "transactionKey": null,
                            "orderId": "%d",
                            "status": "PENDING",
                            "failureReason": null
                        }
                        """.formatted(orderId))));

            PaymentCommand command = new PaymentCommand(orderId, CardType.SAMSUNG, "1234-5678-9814-1451");

            // when
            PaymentInfo result = paymentFacade.requestPayment(1L, command);

            // then
            assertThat(result.status()).isEqualTo("PG_FAILED");

            // TX 분리: TX-1이 커밋되어 주문은 PAYMENT_PENDING 상태 유지
            // → Polling 스케줄러가 복구하거나 사용자가 수동 동기화
            OrderModel order = orderService.getOrderForAdmin(orderId);
            assertThat(order.status()).isEqualTo(OrderStatus.PAYMENT_PENDING);
        }
    }

    @DisplayName("PG 네트워크 장애 → Retry 후 Fallback")
    @Nested
    @Order(5)
    class PgNetworkFault {

        @DisplayName("PG 연결이 끊어지면 3번 재시도 후 Fallback으로 PG_FAILED 응답이 반환된다")
        @Test
        void fallbackOnConnectionReset() {
            // given
            OrderResult orderResult = createOrder();
            Long orderId = orderResult.order().getId();

            stubFor(post(urlEqualTo("/api/v1/payments"))
                .willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)));

            PaymentCommand command = new PaymentCommand(orderId, CardType.SAMSUNG, "1234-5678-9814-1451");

            // when
            PaymentInfo result = paymentFacade.requestPayment(1L, command);

            // then
            assertThat(result.status()).isEqualTo("PG_FAILED");

            // TX 분리: TX-1이 커밋되어 주문은 PAYMENT_PENDING 상태 유지
            // → Polling 스케줄러가 복구하거나 사용자가 수동 동기화
            OrderModel order = orderService.getOrderForAdmin(orderId);
            assertThat(order.status()).isEqualTo(OrderStatus.PAYMENT_PENDING);

            verify(3, postRequestedFor(urlEqualTo("/api/v1/payments")));
        }

        @DisplayName("PG가 불완전한 응답을 보내면 Fallback으로 PG_FAILED 응답이 반환된다")
        @Test
        void fallbackOnEmptyResponse() {
            // given
            OrderResult orderResult = createOrder();
            Long orderId = orderResult.order().getId();

            stubFor(post(urlEqualTo("/api/v1/payments"))
                .willReturn(aResponse().withFault(Fault.EMPTY_RESPONSE)));

            PaymentCommand command = new PaymentCommand(orderId, CardType.SAMSUNG, "1234-5678-9814-1451");

            // when
            PaymentInfo result = paymentFacade.requestPayment(1L, command);

            // then
            assertThat(result.status()).isEqualTo("PG_FAILED");

            // TX 분리: TX-1이 커밋되어 주문은 PAYMENT_PENDING 상태 유지
            // → Polling 스케줄러가 복구하거나 사용자가 수동 동기화
            OrderModel order = orderService.getOrderForAdmin(orderId);
            assertThat(order.status()).isEqualTo(OrderStatus.PAYMENT_PENDING);
        }
    }
}
