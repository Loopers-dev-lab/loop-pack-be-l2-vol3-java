package com.loopers.interfaces.api.payment;

import com.loopers.domain.category.Category;
import com.loopers.domain.category.CategoryRepository;
import com.loopers.domain.payment.PaymentGateway;
import com.loopers.domain.payment.PaymentStatus;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.member.MemberDto;
import com.loopers.interfaces.api.order.OrderDto;
import com.loopers.interfaces.api.product.ProductDto;
import com.loopers.infrastructure.payment.PaymentCompletionPollingScheduler;
import com.loopers.infrastructure.payment.PaymentRecoveryRequiredException;
import com.loopers.testcontainers.MySqlTestContainersConfig;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.context.ImportTestcontainers;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.context.annotation.Import;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ImportTestcontainers(MySqlTestContainersConfig.class)
@ActiveProfiles("test")
@Import(PaymentApiE2ETest.PaymentGatewayTestConfig.class)
class PaymentApiE2ETest {

    private static final String ENDPOINT_PAYMENTS = "/api/v1/payments";
    private static final String ENDPOINT_ORDERS = "/api/v1/orders";
    private static final String ENDPOINT_BRANDS = "/api-admin/v1/brands";
    private static final String HEADER_ADMIN_LDAP = "X-Loopers-Ldap";
    private static final String ADMIN_LDAP_VALUE = "loopers.admin";
    private static final String HEADER_LOGIN_ID = "X-Loopers-LoginId";
    private static final String HEADER_LOGIN_PW = "X-Loopers-LoginPw";
    private static final String TEST_LOGIN_ID = "paymentuser1";
    private static final String TEST_PASSWORD = "Test1234!@";

    @Autowired
    private TestRestTemplate testRestTemplate;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private PaymentCompletionPollingScheduler paymentCompletionPollingScheduler;

    @Autowired
    private FakePaymentGateway fakePaymentGateway;

    private UUID brandId;
    private UUID categoryId;

    @BeforeEach
    void setUp() {
        MemberDto.RegisterRequest registerRequest = new MemberDto.RegisterRequest(
                TEST_LOGIN_ID,
                TEST_PASSWORD,
                "결제자",
                "19900101",
                "payment@test.com",
                "010-1111-2222"
        );
        testRestTemplate.exchange(
                "/api/v1/members",
                HttpMethod.POST,
                new HttpEntity<>(registerRequest),
                new ParameterizedTypeReference<ApiResponse<Void>>() {
                }
        );

        brandId = createBrand("PAYMENT_TEST_BRAND");
        categoryId = createCategory("PAYMENT_TEST_CATEGORY");
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
        fakePaymentGateway.clear();
    }

    @Nested
    @DisplayName("결제 콜백/폴링 수렴")
    class CompletionConvergence {

        @Test
        @DisplayName("콜백으로 결제 상태를 SUCCEEDED 로 수렴시킨다")
        void callbackCompletesPayment() {
            UUID orderId = createOrderForPayment();

            ResponseEntity<ApiResponse<PaymentDto.PaymentResponse>> started = testRestTemplate.exchange(
                    ENDPOINT_PAYMENTS,
                    HttpMethod.POST,
                    new HttpEntity<>(
                            new PaymentDto.StartPaymentRequest(orderId, com.loopers.domain.payment.CardType.SAMSUNG, "1234-5678-1234-5678"),
                            authHeaders()
                    ),
                    new ParameterizedTypeReference<>() {
                    }
            );

            assertThat(started.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            assertThat(started.getBody()).isNotNull();
            String transactionKey = started.getBody().data().transactionKey();
            assertThat(started.getBody().data().status()).isEqualTo("REQUESTED");

            fakePaymentGateway.setStatus(transactionKey, PaymentStatus.SUCCEEDED, null);

            ResponseEntity<ApiResponse<Void>> callback = testRestTemplate.exchange(
                    ENDPOINT_PAYMENTS + "/callback",
                    HttpMethod.POST,
                    new HttpEntity<>(new PaymentCallbackDto.CallbackRequest(TEST_LOGIN_ID, transactionKey), callbackHeaders()),
                    new ParameterizedTypeReference<>() {
                    }
            );

            assertThat(callback.getStatusCode()).isEqualTo(HttpStatus.OK);

            ResponseEntity<ApiResponse<PaymentDto.PaymentListResponse>> queried = testRestTemplate.exchange(
                    ENDPOINT_PAYMENTS + "?orderId=" + orderId,
                    HttpMethod.GET,
                    new HttpEntity<>(authHeaders()),
                    new ParameterizedTypeReference<>() {
                    }
            );

            assertThat(queried.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(queried.getBody()).isNotNull();
            assertThat(queried.getBody().data().payments()).isNotEmpty();
            assertThat(queried.getBody().data().payments().get(0).status()).isEqualTo("SUCCEEDED");
        }

        @Test
        @DisplayName("폴링으로 취소 요청 상태를 CANCELLED 로 수렴시킨다")
        void pollingCompletesCancel() {
            UUID orderId = createOrderForPayment();

            ResponseEntity<ApiResponse<PaymentDto.PaymentResponse>> started = testRestTemplate.exchange(
                    ENDPOINT_PAYMENTS,
                    HttpMethod.POST,
                    new HttpEntity<>(
                            new PaymentDto.StartPaymentRequest(orderId, com.loopers.domain.payment.CardType.SAMSUNG, "1234-5678-1234-5678"),
                            authHeaders()
                    ),
                    new ParameterizedTypeReference<>() {
                    }
            );

            String transactionKey = started.getBody().data().transactionKey();
            fakePaymentGateway.setStatus(transactionKey, PaymentStatus.SUCCEEDED, null);

            ResponseEntity<ApiResponse<Void>> callback = testRestTemplate.exchange(
                    ENDPOINT_PAYMENTS + "/callback",
                    HttpMethod.POST,
                    new HttpEntity<>(new PaymentCallbackDto.CallbackRequest(TEST_LOGIN_ID, transactionKey), callbackHeaders()),
                    new ParameterizedTypeReference<>() {
                    }
            );
            assertThat(callback.getStatusCode()).isEqualTo(HttpStatus.OK);

            ResponseEntity<ApiResponse<PaymentDto.PaymentResponse>> cancelled = testRestTemplate.exchange(
                    ENDPOINT_PAYMENTS + "/" + orderId + "/cancel",
                    HttpMethod.PATCH,
                    new HttpEntity<>(authHeaders()),
                    new ParameterizedTypeReference<>() {
                    }
            );

            assertThat(cancelled.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(cancelled.getBody()).isNotNull();
            assertThat(cancelled.getBody().data().status()).isEqualTo("CANCEL_RECONCILE_REQUIRED");

            fakePaymentGateway.setStatus(transactionKey, PaymentStatus.CANCELLED, null);
            paymentCompletionPollingScheduler.pollPendingPayments();

            ResponseEntity<ApiResponse<PaymentDto.PaymentListResponse>> queried = testRestTemplate.exchange(
                    ENDPOINT_PAYMENTS + "?orderId=" + orderId,
                    HttpMethod.GET,
                    new HttpEntity<>(authHeaders()),
                    new ParameterizedTypeReference<>() {
                    }
            );

            assertThat(queried.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(queried.getBody()).isNotNull();
            assertThat(queried.getBody().data().payments()).isNotEmpty();
            assertThat(queried.getBody().data().payments().get(0).status()).isEqualTo("CANCELLED");
        }
    }

    private UUID createOrderForPayment() {
        UUID productId = createProduct("결제 테스트 상품", 12000, 10);

        OrderDto.CreateOrderRequest createRequest = new OrderDto.CreateOrderRequest(
                List.of(new OrderDto.OrderItemRequest(productId, 2))
        );

        ResponseEntity<ApiResponse<OrderDto.OrderResponse>> created = testRestTemplate.exchange(
                ENDPOINT_ORDERS,
                HttpMethod.POST,
                new HttpEntity<>(createRequest, authHeaders()),
                new ParameterizedTypeReference<>() {
                }
        );

        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(created.getBody()).isNotNull();
        return created.getBody().data().id();
    }

    private UUID createProduct(String name, int price, int stock) {
        ProductDto.CreateProductRequest request = new ProductDto.CreateProductRequest(
                name,
                price,
                stock,
                "설명",
                categoryId,
                brandId
        );
        ResponseEntity<ApiResponse<ProductDto.ProductResponse>> response = testRestTemplate.exchange(
                "/api/v1/products",
                HttpMethod.POST,
                new HttpEntity<>(request),
                new ParameterizedTypeReference<>() {
                }
        );
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().data()).isNotNull();
        return response.getBody().data().id();
    }

    private UUID createBrand(String name) {
        var request = new com.loopers.interfaces.api.brand.BrandDto.CreateBrandRequest(
                name,
                "결제 테스트 브랜드",
                "https://example.com/logo.png"
        );

        HttpHeaders headers = new HttpHeaders();
        headers.set(HEADER_ADMIN_LDAP, ADMIN_LDAP_VALUE);

        ResponseEntity<com.loopers.interfaces.api.ApiResponse<com.loopers.interfaces.api.brand.BrandDto.BrandResponse>> response =
                testRestTemplate.exchange(
                        ENDPOINT_BRANDS,
                        HttpMethod.POST,
                        new HttpEntity<>(request, headers),
                        new ParameterizedTypeReference<>() {
                        }
                );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().data()).isNotNull();

        return response.getBody().data().id();
    }

    private UUID createCategory(String name) {
        return categoryRepository.save(new Category(name)).id();
    }

    private HttpHeaders authHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HEADER_LOGIN_ID, TEST_LOGIN_ID);
        headers.set(HEADER_LOGIN_PW, TEST_PASSWORD);
        return headers;
    }

    private HttpHeaders callbackHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-USER-ID", TEST_LOGIN_ID);
        return headers;
    }

    @TestConfiguration
    static class PaymentGatewayTestConfig {
        @Bean
        @Primary
        FakePaymentGateway fakePaymentGateway() {
            return new FakePaymentGateway();
        }
    }

    static class FakePaymentGateway implements PaymentGateway {

        private final AtomicInteger sequence = new AtomicInteger();
        private final Map<String, PaymentGatewayTransaction> byTransactionKey = new ConcurrentHashMap<>();
        private final Map<String, String> orderToTransaction = new ConcurrentHashMap<>();
        private volatile boolean forceCancelRecoveryRequired = true;

        @Override
        public PaymentGatewayTransaction requestPayment(PaymentGatewayRequest request) {
            String key = "TEST-TRX-" + sequence.incrementAndGet();
            PaymentGatewayTransaction transaction = new PaymentGatewayTransaction(
                    key,
                    request.orderReference(),
                    PaymentStatus.REQUESTED,
                    null
            );
            byTransactionKey.put(key, transaction);
            orderToTransaction.put(request.orderReference(), key);
            return transaction;
        }

        @Override
        public PaymentGatewayTransaction cancelPayment(PaymentGatewayCancelRequest request) {
            PaymentGatewayTransaction found = byTransactionKey.get(request.transactionKey());
            if (found == null) {
                throw new com.loopers.support.error.CoreException(com.loopers.support.error.ErrorType.NOT_FOUND, "결제건이 없습니다.");
            }
            if (forceCancelRecoveryRequired) {
                forceCancelRecoveryRequired = false;
                throw new PaymentRecoveryRequiredException("결제 취소 결과가 불명확하며 상태 조회에도 실패했습니다.");
            }
            PaymentGatewayTransaction next = new PaymentGatewayTransaction(
                    found.transactionKey(),
                    found.orderReference(),
                    PaymentStatus.CANCEL_REQUESTED,
                    null
            );
            byTransactionKey.put(found.transactionKey(), next);
            return next;
        }

        @Override
        public PaymentGatewayTransaction getPayment(String memberId, String transactionKey) {
            PaymentGatewayTransaction found = byTransactionKey.get(transactionKey);
            if (found == null) {
                throw new com.loopers.support.error.CoreException(com.loopers.support.error.ErrorType.NOT_FOUND, "결제건이 없습니다.");
            }
            return found;
        }

        @Override
        public List<PaymentGatewayTransaction> getPaymentsByOrderId(String memberId, String orderReference) {
            String key = orderToTransaction.get(orderReference);
            if (key == null) {
                return List.of();
            }
            return List.of(byTransactionKey.get(key));
        }

        void setStatus(String transactionKey, PaymentStatus status, String reason) {
            PaymentGatewayTransaction found = byTransactionKey.get(transactionKey);
            if (found == null) {
                throw new com.loopers.support.error.CoreException(com.loopers.support.error.ErrorType.NOT_FOUND, "결제건이 없습니다.");
            }
            byTransactionKey.put(transactionKey, new PaymentGatewayTransaction(
                    found.transactionKey(),
                    found.orderReference(),
                    status,
                    reason
            ));
        }

        void clear() {
            byTransactionKey.clear();
            orderToTransaction.clear();
            sequence.set(0);
            forceCancelRecoveryRequired = true;
        }
    }
}
