package com.loopers.interfaces.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.order.OrderItemModel;
import com.loopers.domain.order.OrderModel;
import com.loopers.domain.order.OrderStatus;
import com.loopers.domain.payment.CardType;
import com.loopers.domain.payment.PaymentModel;
import com.loopers.domain.payment.PaymentStatus;
import com.loopers.domain.product.ProductModel;
import com.loopers.infrastructure.brand.BrandJpaRepository;
import com.loopers.infrastructure.order.OrderJpaRepository;
import com.loopers.infrastructure.payment.PaymentJpaRepository;
import com.loopers.infrastructure.product.ProductJpaRepository;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.payment.dto.PgCallbackRequest;
import com.loopers.interfaces.user.dto.UserV1Dto;
import com.loopers.utils.DatabaseCleanUp;
import java.util.List;
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
import org.springframework.http.ResponseEntity;

@DisplayName("Payment V1 API E2E 테스트")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class PaymentV1ApiE2ETest {

    private static final String HEADER_LOGIN_ID = "X-Loopers-LoginId";
    private static final String HEADER_LOGIN_PW = "X-Loopers-LoginPw";

    private final TestRestTemplate testRestTemplate;
    private final BrandJpaRepository brandJpaRepository;
    private final ProductJpaRepository productJpaRepository;
    private final OrderJpaRepository orderJpaRepository;
    private final PaymentJpaRepository paymentJpaRepository;
    private final DatabaseCleanUp databaseCleanUp;

    private Long orderId;

    @Autowired
    public PaymentV1ApiE2ETest(
            TestRestTemplate testRestTemplate,
            BrandJpaRepository brandJpaRepository,
            ProductJpaRepository productJpaRepository,
            OrderJpaRepository orderJpaRepository,
            PaymentJpaRepository paymentJpaRepository,
            DatabaseCleanUp databaseCleanUp) {
        this.testRestTemplate = testRestTemplate;
        this.brandJpaRepository = brandJpaRepository;
        this.productJpaRepository = productJpaRepository;
        this.orderJpaRepository = orderJpaRepository;
        this.paymentJpaRepository = paymentJpaRepository;
        this.databaseCleanUp = databaseCleanUp;
    }

    @BeforeEach
    void setUp() {
        testRestTemplate.exchange(
                "/api/v1/users/signup", HttpMethod.POST,
                new HttpEntity<>(new UserV1Dto.SignupRequest(
                        "testuser1", "Test1234!", "홍길동",
                        "19900101", "test@example.com")),
                new ParameterizedTypeReference<ApiResponse<UserV1Dto.SignupResponse>>() {});

        BrandModel brand = brandJpaRepository.save(BrandModel.create("ACNE STUDIOS"));
        ProductModel product = productJpaRepository.save(
                ProductModel.create(brand.getId(), "오버사이즈 코트", 50000, 100));
        product.decreaseStock(2);
        productJpaRepository.save(product);

        OrderModel order = orderJpaRepository.save(
                OrderModel.createPendingPayment(1L, List.of(
                        OrderItemModel.create(
                                product.getId(), 50000, 2,
                                "오버사이즈 코트", "ACNE STUDIOS"))));
        orderId = order.getId();
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    private HttpHeaders authHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HEADER_LOGIN_ID, "testuser1");
        headers.set(HEADER_LOGIN_PW, "Test1234!");
        return headers;
    }

    @DisplayName("POST /api/v1/payments/callback")
    @Nested
    class HandleCallback {

        @DisplayName("SUCCESS 콜백 수신하면 Payment가 COMPLETED, Order가 ORDERED로 전이된다.")
        @Test
        void handleCallback_success_completesPaymentAndOrder() {
            // arrange — Payment를 DB에 직접 생성 후 REQUESTED로 전이
            PaymentModel payment = PaymentModel.create(
                    orderId, 100000, CardType.SAMSUNG, "****-****-****-1451");
            payment.requested("E2E_TEST_PK_001");
            paymentJpaRepository.save(payment);

            // act — SUCCESS 콜백
            ResponseEntity<ApiResponse<Object>> response = testRestTemplate.exchange(
                    "/api/v1/payments/callback", HttpMethod.POST,
                    new HttpEntity<>(new PgCallbackRequest(
                            "E2E_TEST_PK_001", String.valueOf(orderId),
                            "SAMSUNG", "1234-5678-9814-1451",
                            100000, "SUCCESS", "정상 승인되었습니다.")),
                    new ParameterizedTypeReference<>() {});

            // assert
            PaymentModel updatedPayment = paymentJpaRepository
                    .findByOrderIdAndDeletedAtIsNull(orderId).orElseThrow();
            OrderModel updatedOrder = orderJpaRepository.findById(orderId).orElseThrow();

            assertAll(
                    () -> assertThat(response.getStatusCode().is2xxSuccessful()).isTrue(),
                    () -> assertThat(updatedPayment.getStatus())
                            .isEqualTo(PaymentStatus.COMPLETED),
                    () -> assertThat(updatedOrder.getStatus())
                            .isEqualTo(OrderStatus.ORDERED));
        }

        @DisplayName("FAILED 콜백 수신하면 Payment는 FAILED로 전이된다.")
        @Test
        void handleCallback_failed_failsPayment() {
            // arrange
            PaymentModel payment = PaymentModel.create(
                    orderId, 100000, CardType.SAMSUNG, "****-****-****-1451");
            payment.requested("E2E_TEST_PK_002");
            paymentJpaRepository.save(payment);

            // act — FAILED 콜백
            ResponseEntity<ApiResponse<Object>> response = testRestTemplate.exchange(
                    "/api/v1/payments/callback", HttpMethod.POST,
                    new HttpEntity<>(new PgCallbackRequest(
                            "E2E_TEST_PK_002", String.valueOf(orderId),
                            "SAMSUNG", "1234-5678-9814-1451",
                            100000, "FAILED", "한도초과입니다.")),
                    new ParameterizedTypeReference<ApiResponse<Object>>() {});

            // assert
            PaymentModel updatedPayment = paymentJpaRepository
                    .findByOrderIdAndDeletedAtIsNull(orderId).orElseThrow();

            assertAll(
                    () -> assertThat(response.getStatusCode().is2xxSuccessful()).isTrue(),
                    () -> assertThat(updatedPayment.getStatus())
                            .isEqualTo(PaymentStatus.FAILED));
        }
    }
}
