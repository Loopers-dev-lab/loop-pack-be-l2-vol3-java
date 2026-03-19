package com.loopers.interfaces.api.payment;

import com.loopers.domain.brand.BrandService;
import com.loopers.domain.product.ProductService;
import com.loopers.infrastructure.payment.PgSimulatorClient;
import com.loopers.infrastructure.payment.PgSimulatorRequest;
import com.loopers.infrastructure.payment.PgSimulatorResponse;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.order.OrderV1Dto;
import com.loopers.interfaces.api.user.UserV1Dto;
import com.loopers.testcontainers.MySqlTestContainersConfig;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

import static com.loopers.interfaces.api.ApiResponse.Metadata.Result;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

/**
 * 결제 API E2E (06-payment-change-issues §3.1, §7). PG는 MockBean으로 격리.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(MySqlTestContainersConfig.class)
class PaymentV1ApiE2ETest {

    private static final String LOGIN_ID = "paye2euser";
    private static final String ENDPOINT_PAYMENTS = "/api/v1/payments";
    private static final String ENDPOINT_CALLBACK = "/api/v1/payments/callback";

    @Autowired
    private TestRestTemplate testRestTemplate;
    @Autowired
    private DatabaseCleanUp databaseCleanUp;
    @Autowired
    private BrandService brandService;
    @Autowired
    private ProductService productService;

    @MockBean
    private PgSimulatorClient pgSimulatorClient;

    private Long productId;

    @BeforeEach
    void setUp() {
        when(pgSimulatorClient.requestPayment(any(PgSimulatorRequest.class)))
                .thenReturn(new PgSimulatorResponse("e2e-tx"));

        UserV1Dto.SignUpRequest signUp = new UserV1Dto.SignUpRequest(
                LOGIN_ID, "SecurePass1!", "paye2e@example.com", "1990-01-15", "MALE");
        testRestTemplate.exchange("/api/v1/users", HttpMethod.POST, new HttpEntity<>(signUp),
                new ParameterizedTypeReference<ApiResponse<UserV1Dto.SignUpResponse>>() {
                });
        var brand = brandService.registerBrand("PayE2EBrand");
        var product = productService.registerProduct(brand.getId(), "PayE2EItem", new BigDecimal("12000"), 15);
        productId = product.getId();
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    private HttpHeaders authHeaders() {
        HttpHeaders h = new HttpHeaders();
        h.set("X-Loopers-LoginId", LOGIN_ID);
        h.setContentType(MediaType.APPLICATION_JSON);
        return h;
    }

    private Long createOrderedOrderViaApi() {
        OrderV1Dto.CreateOrderRequest req = new OrderV1Dto.CreateOrderRequest(
                List.of(new OrderV1Dto.OrderItemRequest(productId, 1, null)), null);
        ResponseEntity<ApiResponse<OrderV1Dto.OrderResponse>> res = testRestTemplate.exchange(
                "/api/v1/orders", HttpMethod.POST, new HttpEntity<>(req, authHeaders()),
                new ParameterizedTypeReference<>() {
                });
        return res.getBody().data().id();
    }

    @Nested
    @DisplayName("POST /api/v1/payments")
    class RequestPayment {

        @Test
        void requestPayment_withValidRequest_shouldReturn200WithPENDING() {
            Long orderId = createOrderedOrderViaApi();
            PaymentV1Dto.PaymentRequest body = new PaymentV1Dto.PaymentRequest(orderId, "SAMSUNG", "1111-2222");

            ResponseEntity<ApiResponse<PaymentV1Dto.PaymentResponse>> response = testRestTemplate.exchange(
                    ENDPOINT_PAYMENTS, HttpMethod.POST, new HttpEntity<>(body, authHeaders()),
                    new ParameterizedTypeReference<>() {
                    });

            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(response.getBody().meta().result()).isEqualTo(Result.SUCCESS),
                    () -> assertThat(response.getBody().data().orderId()).isEqualTo(orderId),
                    () -> assertThat(response.getBody().data().status()).isEqualTo("PENDING")
            );
        }

        @Test
        void requestPayment_withoutLogin_shouldReturn401() {
            Long orderId = createOrderedOrderViaApi();
            PaymentV1Dto.PaymentRequest body = new PaymentV1Dto.PaymentRequest(orderId, "SAMSUNG", "1");
            HttpHeaders h = new HttpHeaders();
            h.setContentType(MediaType.APPLICATION_JSON);

            ResponseEntity<ApiResponse<PaymentV1Dto.PaymentResponse>> response = testRestTemplate.exchange(
                    ENDPOINT_PAYMENTS, HttpMethod.POST, new HttpEntity<>(body, h),
                    new ParameterizedTypeReference<>() {
                    });

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }

        @Test
        void requestPayment_whenDuplicatePending_shouldReturn409() {
            Long orderId = createOrderedOrderViaApi();
            PaymentV1Dto.PaymentRequest body = new PaymentV1Dto.PaymentRequest(orderId, "SAMSUNG", "1");
            testRestTemplate.exchange(
                    ENDPOINT_PAYMENTS, HttpMethod.POST, new HttpEntity<>(body, authHeaders()),
                    new ParameterizedTypeReference<ApiResponse<PaymentV1Dto.PaymentResponse>>() {
                    });

            ResponseEntity<ApiResponse<PaymentV1Dto.PaymentResponse>> second = testRestTemplate.exchange(
                    ENDPOINT_PAYMENTS, HttpMethod.POST, new HttpEntity<>(body, authHeaders()),
                    new ParameterizedTypeReference<>() {
                    });

            assertThat(second.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        }

        @Test
        void requestPayment_whenPgThrows_shouldStillReturn200WithPENDING() {
            doThrow(new RuntimeException("pg down"))
                    .when(pgSimulatorClient).requestPayment(any(PgSimulatorRequest.class));
            Long orderId = createOrderedOrderViaApi();
            PaymentV1Dto.PaymentRequest body = new PaymentV1Dto.PaymentRequest(orderId, "SAMSUNG", "1");

            ResponseEntity<ApiResponse<PaymentV1Dto.PaymentResponse>> response = testRestTemplate.exchange(
                    ENDPOINT_PAYMENTS, HttpMethod.POST, new HttpEntity<>(body, authHeaders()),
                    new ParameterizedTypeReference<>() {
                    });

            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(response.getBody().data().status()).isEqualTo("PENDING")
            );
        }
    }

    @Nested
    @DisplayName("POST /api/v1/payments/callback")
    class Callback {

        @Test
        void paymentCallback_whenSuccess_shouldMarkOrderPaid() {
            Long orderId = createOrderedOrderViaApi();
            PaymentV1Dto.PaymentRequest payReq = new PaymentV1Dto.PaymentRequest(orderId, "SAMSUNG", "1");
            testRestTemplate.exchange(
                    ENDPOINT_PAYMENTS, HttpMethod.POST, new HttpEntity<>(payReq, authHeaders()),
                    new ParameterizedTypeReference<ApiResponse<PaymentV1Dto.PaymentResponse>>() {
                    });

            ResponseEntity<ApiResponse<OrderV1Dto.OrderResponse>> orderBefore = testRestTemplate.exchange(
                    "/api/v1/orders/" + orderId, HttpMethod.GET, new HttpEntity<>(authHeaders()),
                    new ParameterizedTypeReference<>() {
                    });
            long amountWon = orderBefore.getBody().data().finalAmount()
                    .setScale(0, RoundingMode.HALF_UP).longValue();

            String callbackJson = """
                    {"paymentId":"pg-p1","orderId":%d,"success":true,"amount":%d}
                    """.formatted(orderId, amountWon);
            HttpHeaders cbHeaders = new HttpHeaders();
            cbHeaders.setContentType(MediaType.APPLICATION_JSON);
            ResponseEntity<Void> cbRes = testRestTemplate.exchange(
                    ENDPOINT_CALLBACK, HttpMethod.POST, new HttpEntity<>(callbackJson, cbHeaders), Void.class);

            assertThat(cbRes.getStatusCode()).isEqualTo(HttpStatus.OK);

            ResponseEntity<ApiResponse<OrderV1Dto.OrderResponse>> orderAfter = testRestTemplate.exchange(
                    "/api/v1/orders/" + orderId, HttpMethod.GET, new HttpEntity<>(authHeaders()),
                    new ParameterizedTypeReference<>() {
                    });
            assertThat(orderAfter.getBody().data().status()).isEqualTo("PAID");
        }

        @Test
        void paymentCallback_whenFailure_shouldKeepOrderORDERED() {
            Long orderId = createOrderedOrderViaApi();
            PaymentV1Dto.PaymentRequest payReq = new PaymentV1Dto.PaymentRequest(orderId, "SAMSUNG", "1");
            testRestTemplate.exchange(
                    ENDPOINT_PAYMENTS, HttpMethod.POST, new HttpEntity<>(payReq, authHeaders()),
                    new ParameterizedTypeReference<ApiResponse<PaymentV1Dto.PaymentResponse>>() {
                    });

            String callbackJson = """
                    {"paymentId":"pg-p2","orderId":%d,"success":false,"failureReason":"LIMIT"}
                    """.formatted(orderId);
            HttpHeaders cbHeaders = new HttpHeaders();
            cbHeaders.setContentType(MediaType.APPLICATION_JSON);
            testRestTemplate.exchange(
                    ENDPOINT_CALLBACK, HttpMethod.POST, new HttpEntity<>(callbackJson, cbHeaders), Void.class);

            ResponseEntity<ApiResponse<OrderV1Dto.OrderResponse>> orderAfter = testRestTemplate.exchange(
                    "/api/v1/orders/" + orderId, HttpMethod.GET, new HttpEntity<>(authHeaders()),
                    new ParameterizedTypeReference<>() {
                    });
            assertThat(orderAfter.getBody().data().status()).isEqualTo("ORDERED");
        }
    }
}
