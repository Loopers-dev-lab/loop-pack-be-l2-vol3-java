package com.loopers.application.payment;

import com.loopers.application.brand.BrandService;
import com.loopers.application.order.OrderFacade;
import com.loopers.application.order.OrderItemCommand;
import com.loopers.application.order.OrderResult;
import com.loopers.application.order.OrderService;
import com.loopers.domain.order.OrderModel;
import com.loopers.domain.order.OrderStatus;
import com.loopers.domain.payment.CardType;
import com.loopers.domain.product.Money;
import com.loopers.application.product.ProductFacade;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

@SpringBootTest
class PaymentFacadeIntegrationTest {

    @Autowired private PaymentFacade paymentFacade;
    @Autowired private PaymentService paymentService;
    @Autowired private OrderFacade orderFacade;
    @Autowired private OrderService orderService;
    @Autowired private ProductFacade productFacade;
    @Autowired private BrandService brandService;
    @Autowired private DatabaseCleanUp databaseCleanUp;
    @Autowired @Qualifier("pgRestTemplate") private RestTemplate pgRestTemplate;

    private MockRestServiceServer mockServer;

    @BeforeEach
    void setUp() {
        mockServer = MockRestServiceServer.createServer(pgRestTemplate);
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
        mockServer.reset();
    }

    private OrderResult createOrder() {
        Long brandId = brandService.register("나이키", "스포츠").getId();
        Long productId = productFacade.register("에어맥스", "러닝화", new Money(50000), brandId, 10).id();
        return orderFacade.placeOrder(1L, List.of(new OrderItemCommand(productId, 1)), null);
    }

    @DisplayName("결제 요청")
    @Nested
    class RequestPayment {

        @DisplayName("PG 요청 성공 시 PENDING 결제가 생성되고 주문 상태가 PAYMENT_PENDING으로 변경된다")
        @Test
        void createsPendingPayment() {
            // given
            OrderResult orderResult = createOrder();
            Long orderId = orderResult.order().getId();

            mockServer.expect(requestTo("http://localhost:8082/api/v1/payments"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(
                    "{\"transactionKey\":\"20250816:TR:test123\",\"orderId\":\"%d\",\"status\":\"PENDING\",\"failureReason\":null}"
                        .formatted(orderId),
                    MediaType.APPLICATION_JSON
                ));

            PaymentCommand command = new PaymentCommand(orderId, CardType.SAMSUNG, "1234-5678-9814-1451");

            // when
            PaymentInfo result = paymentFacade.requestPayment(1L, command);

            // then
            assertAll(
                () -> assertThat(result.status()).isEqualTo("PENDING"),
                () -> assertThat(result.transactionKey()).isEqualTo("20250816:TR:test123"),
                () -> assertThat(result.maskedCardNo()).isEqualTo("1234-****-****-1451"),
                () -> assertThat(result.amount()).isEqualTo(50000)
            );

            OrderModel order = orderService.getOrderForAdmin(orderId);
            assertThat(order.status()).isEqualTo(OrderStatus.PAYMENT_PENDING);

            mockServer.verify();
        }
    }

    @DisplayName("콜백 처리")
    @Nested
    class HandleCallback {

        @DisplayName("SUCCESS 콜백 시 결제 성공 + 주문 CONFIRMED 처리")
        @Test
        void handlesSuccessCallback() {
            // given
            OrderResult orderResult = createOrder();
            Long orderId = orderResult.order().getId();

            mockServer.expect(requestTo("http://localhost:8082/api/v1/payments"))
                .andRespond(withSuccess(
                    "{\"transactionKey\":\"20250816:TR:cb001\",\"orderId\":\"%d\",\"status\":\"PENDING\",\"failureReason\":null}"
                        .formatted(orderId),
                    MediaType.APPLICATION_JSON
                ));

            PaymentCommand command = new PaymentCommand(orderId, CardType.SAMSUNG, "1234-5678-9814-1451");
            paymentFacade.requestPayment(1L, command);

            // when
            paymentFacade.handleCallback("20250816:TR:cb001", "SUCCESS", null);

            // then
            PaymentInfo payment = paymentFacade.getPaymentsByOrderId(orderId).get(0);
            assertThat(payment.status()).isEqualTo("SUCCESS");

            OrderModel order = orderService.getOrderForAdmin(orderId);
            assertThat(order.status()).isEqualTo(OrderStatus.CONFIRMED);
        }

        @DisplayName("FAILED 콜백 시 결제 실패 + 주문 PAYMENT_FAILED 처리")
        @Test
        void handlesFailedCallback() {
            // given
            OrderResult orderResult = createOrder();
            Long orderId = orderResult.order().getId();

            mockServer.expect(requestTo("http://localhost:8082/api/v1/payments"))
                .andRespond(withSuccess(
                    "{\"transactionKey\":\"20250816:TR:cb002\",\"orderId\":\"%d\",\"status\":\"PENDING\",\"failureReason\":null}"
                        .formatted(orderId),
                    MediaType.APPLICATION_JSON
                ));

            PaymentCommand command = new PaymentCommand(orderId, CardType.SAMSUNG, "1234-5678-9814-1451");
            paymentFacade.requestPayment(1L, command);

            // when
            paymentFacade.handleCallback("20250816:TR:cb002", "FAILED", "한도 초과");

            // then
            PaymentInfo payment = paymentFacade.getPaymentsByOrderId(orderId).get(0);
            assertAll(
                () -> assertThat(payment.status()).isEqualTo("FAILED"),
                () -> assertThat(payment.failureReason()).isEqualTo("한도 초과")
            );

            OrderModel order = orderService.getOrderForAdmin(orderId);
            assertThat(order.status()).isEqualTo(OrderStatus.PAYMENT_FAILED);
        }
    }
}
