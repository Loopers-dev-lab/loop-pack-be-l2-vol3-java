package com.loopers.interfaces.api.payment;

import com.loopers.application.coupon.CouponAdminApplicationService;
import com.loopers.application.coupon.CouponApplicationService;
import com.loopers.application.coupon.command.CreateCouponCommand;
import com.loopers.application.product.ProductApplicationService;
import com.loopers.domain.coupon.Coupon;
import com.loopers.domain.coupon.CouponStatus;
import com.loopers.domain.coupon.CouponType;
import com.loopers.domain.coupon.IssuedCoupon;
import com.loopers.domain.coupon.IssuedCouponRepository;
import com.loopers.domain.category.Category;
import com.loopers.domain.category.CategoryRepository;
import com.loopers.domain.point.PointBalance;
import com.loopers.domain.point.PointBalanceRepository;
import com.loopers.domain.payment.PaymentGateway;
import com.loopers.domain.payment.PaymentStatus;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.member.MemberDto;
import com.loopers.interfaces.api.order.OrderDto;
import com.loopers.interfaces.api.product.ProductDto;
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

import java.time.LocalDateTime;
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
    private static final int DEFAULT_INITIAL_POINT_BALANCE = 1_000_000;

    @Autowired
    private TestRestTemplate testRestTemplate;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private ProductApplicationService productApplicationService;

    @Autowired
    private CouponAdminApplicationService couponAdminApplicationService;

    @Autowired
    private CouponApplicationService couponApplicationService;

    @Autowired
    private IssuedCouponRepository issuedCouponRepository;

    @Autowired
    private PointBalanceRepository pointBalanceRepository;

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
    @DisplayName("결제 완료 수렴")
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
        @DisplayName("콜백이 없어도 수동 reconcile 호출로 결제 상태를 SUCCEEDED 로 수렴시킨다")
        void manualReconcileCompletesPaymentWithoutCallback() {
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
            String transactionKey = started.getBody().data().transactionKey();
            fakePaymentGateway.setStatus(transactionKey, PaymentStatus.SUCCEEDED, null);

            ResponseEntity<ApiResponse<PaymentDto.PaymentResponse>> reconciled = testRestTemplate.exchange(
                    ENDPOINT_PAYMENTS + "/" + orderId + "/reconcile",
                    HttpMethod.POST,
                    new HttpEntity<>(authHeaders()),
                    new ParameterizedTypeReference<>() {
                    }
            );

            assertThat(reconciled.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(reconciled.getBody()).isNotNull();
            assertThat(reconciled.getBody().data().status()).isEqualTo("SUCCEEDED");
        }

        @Test
        @DisplayName("PG 요청 결과가 불명확해도 시작 API는 정상 응답하고 reconcile 로 상태를 복구한다")
        void startPaymentRespondsNormallyAndReconcilesWhenRequestResultUncertain() {
            UUID orderId = createOrderForPayment();
            fakePaymentGateway.forceNextRequestRecoveryRequired();

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
            assertThat(started.getBody().data().status()).isEqualTo("REQUESTED");
            assertThat(started.getBody().data().transactionKey()).isNull();

            String recoveredTransactionKey = fakePaymentGateway.getTransactionKeyByOrderReference(orderId.toString());
            assertThat(recoveredTransactionKey).isNotNull();
            fakePaymentGateway.setStatus(recoveredTransactionKey, PaymentStatus.SUCCEEDED, null);

            ResponseEntity<ApiResponse<PaymentDto.PaymentResponse>> reconciled = testRestTemplate.exchange(
                    ENDPOINT_PAYMENTS + "/" + orderId + "/reconcile",
                    HttpMethod.POST,
                    new HttpEntity<>(authHeaders()),
                    new ParameterizedTypeReference<>() {
                    }
            );

            assertThat(reconciled.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(reconciled.getBody()).isNotNull();
            assertThat(reconciled.getBody().data().status()).isEqualTo("SUCCEEDED");
            assertThat(reconciled.getBody().data().transactionKey()).isEqualTo(recoveredTransactionKey);
        }

    }

    @Nested
    @DisplayName("주문 취소 시 결제 상태별 보상/취소 호출")
    class CancelCompensationByPaymentStatus {

        @Test
        @DisplayName("결제 FAILED 상태에서 주문 취소하면 보상은 수행되고 PG 취소 호출은 생략된다")
        void cancelOrderSkipsPaymentCancelWhenPaymentFailed() {
            UUID productId = createProduct("실패보상 상품", 12000, 10);
            int quantity = 2;
            int usedPointAmount = 3000;
            UUID couponId = issueCouponToTestMember("실패보상 쿠폰", 1000);

            ResponseEntity<ApiResponse<OrderDto.OrderResponse>> created = testRestTemplate.exchange(
                    ENDPOINT_ORDERS,
                    HttpMethod.POST,
                    new HttpEntity<>(
                            new OrderDto.CreateOrderRequest(
                                    List.of(new OrderDto.OrderItemRequest(productId, quantity)),
                                    couponId,
                                    usedPointAmount,
                                    com.loopers.domain.payment.CardType.SAMSUNG,
                                    "1234-5678-1234-5678"
                            ),
                            authHeaders()
                    ),
                    new ParameterizedTypeReference<>() {
                    }
            );
            assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            UUID orderId = created.getBody().data().id();

            IssuedCoupon usedCoupon = issuedCouponRepository.findByMemberIdAndCouponId(TEST_LOGIN_ID, couponId)
                    .orElseThrow();
            assertThat(usedCoupon.status()).isEqualTo(CouponStatus.USED);

            PointBalance pointAfterOrder = pointBalanceRepository.findByMemberId(TEST_LOGIN_ID).orElseThrow();
            assertThat(pointAfterOrder.balance()).isEqualTo(DEFAULT_INITIAL_POINT_BALANCE - usedPointAmount);

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
            String transactionKey = started.getBody().data().transactionKey();

            fakePaymentGateway.setStatus(transactionKey, PaymentStatus.FAILED, "결제 실패");

            ResponseEntity<ApiResponse<Void>> callback = testRestTemplate.exchange(
                    ENDPOINT_PAYMENTS + "/callback",
                    HttpMethod.POST,
                    new HttpEntity<>(new PaymentCallbackDto.CallbackRequest(TEST_LOGIN_ID, transactionKey), callbackHeaders()),
                    new ParameterizedTypeReference<>() {
                    }
            );
            assertThat(callback.getStatusCode()).isEqualTo(HttpStatus.OK);

            assertThat(getLatestPaymentStatus(orderId)).isEqualTo("FAILED");

            ResponseEntity<ApiResponse<OrderDto.OrderResponse>> cancelled = testRestTemplate.exchange(
                    ENDPOINT_ORDERS + "/" + orderId + "/cancel",
                    HttpMethod.PATCH,
                    new HttpEntity<>(authHeaders()),
                    new ParameterizedTypeReference<>() {
                    }
            );
            assertThat(cancelled.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(cancelled.getBody().data().status()).isEqualTo("CANCELLED");

            IssuedCoupon restoredCoupon = issuedCouponRepository.findByMemberIdAndCouponId(TEST_LOGIN_ID, couponId)
                    .orElseThrow();
            assertThat(restoredCoupon.status()).isEqualTo(CouponStatus.AVAILABLE);

            PointBalance pointAfterCancel = pointBalanceRepository.findByMemberId(TEST_LOGIN_ID).orElseThrow();
            assertThat(pointAfterCancel.balance()).isEqualTo(DEFAULT_INITIAL_POINT_BALANCE);
            assertThat(productApplicationService.get(productId).stock()).isEqualTo(10);

            assertThat(fakePaymentGateway.cancelCallCount()).isEqualTo(0);
        }

        @Test
        @DisplayName("결제 CANCEL_FAILED 상태에서 주문 취소하면 보상 후 PG 취소를 재시도한다")
        void cancelOrderRetriesPaymentCancelWhenPaymentCancelFailed() {
            UUID productId = createProduct("취소실패보상 상품", 12000, 10);
            int quantity = 2;
            int usedPointAmount = 3000;
            UUID couponId = issueCouponToTestMember("취소실패보상 쿠폰", 1000);

            ResponseEntity<ApiResponse<OrderDto.OrderResponse>> created = testRestTemplate.exchange(
                    ENDPOINT_ORDERS,
                    HttpMethod.POST,
                    new HttpEntity<>(
                            new OrderDto.CreateOrderRequest(
                                    List.of(new OrderDto.OrderItemRequest(productId, quantity)),
                                    couponId,
                                    usedPointAmount,
                                    com.loopers.domain.payment.CardType.SAMSUNG,
                                    "1234-5678-1234-5678"
                            ),
                            authHeaders()
                    ),
                    new ParameterizedTypeReference<>() {
                    }
            );
            assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            UUID orderId = created.getBody().data().id();

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
            assertThat(getLatestPaymentStatus(orderId)).isEqualTo("SUCCEEDED");

            fakePaymentGateway.setForceCancelRecoveryRequired(false);
            fakePaymentGateway.forceNextCancelFailed("PG 취소 실패");

            ResponseEntity<ApiResponse<PaymentDto.PaymentResponse>> cancelPaymentResponse = testRestTemplate.exchange(
                    ENDPOINT_PAYMENTS + "/" + orderId + "/cancel",
                    HttpMethod.PATCH,
                    new HttpEntity<>(authHeaders()),
                    new ParameterizedTypeReference<>() {
                    }
            );

            assertThat(cancelPaymentResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(cancelPaymentResponse.getBody().data().status()).isEqualTo("CANCEL_FAILED");
            assertThat(fakePaymentGateway.cancelCallCount()).isEqualTo(1);

            ResponseEntity<ApiResponse<OrderDto.OrderResponse>> cancelled = testRestTemplate.exchange(
                    ENDPOINT_ORDERS + "/" + orderId + "/cancel",
                    HttpMethod.PATCH,
                    new HttpEntity<>(authHeaders()),
                    new ParameterizedTypeReference<>() {
                    }
            );
            assertThat(cancelled.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(cancelled.getBody().data().status()).isEqualTo("CANCELLED");

            awaitCancelCallCountAtLeast(2);

            IssuedCoupon restoredCoupon = issuedCouponRepository.findByMemberIdAndCouponId(TEST_LOGIN_ID, couponId)
                    .orElseThrow();
            assertThat(restoredCoupon.status()).isEqualTo(CouponStatus.AVAILABLE);

            PointBalance pointAfterCancel = pointBalanceRepository.findByMemberId(TEST_LOGIN_ID).orElseThrow();
            assertThat(pointAfterCancel.balance()).isEqualTo(DEFAULT_INITIAL_POINT_BALANCE);
        }
    }

    @Nested
    @DisplayName("결제 idempotency")
    class PaymentIdempotency {

        @Test
        @DisplayName("동일 주문에 대한 결제 시작 중복 요청은 기존 결제 건을 반환한다")
        void startPaymentDuplicateRequestReturnsExisting() {
            UUID orderId = createOrderForPayment();

            ResponseEntity<ApiResponse<PaymentDto.PaymentResponse>> firstStart = testRestTemplate.exchange(
                    ENDPOINT_PAYMENTS,
                    HttpMethod.POST,
                    new HttpEntity<>(
                            new PaymentDto.StartPaymentRequest(orderId, com.loopers.domain.payment.CardType.SAMSUNG, "1234-5678-1234-5678"),
                            authHeaders()
                    ),
                    new ParameterizedTypeReference<>() {
                    }
            );

            assertThat(firstStart.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            assertThat(firstStart.getBody()).isNotNull();
            assertThat(firstStart.getBody().data().transactionKey()).isNotNull();

            String transactionKey = firstStart.getBody().data().transactionKey();

            ResponseEntity<ApiResponse<PaymentDto.PaymentResponse>> secondStart = testRestTemplate.exchange(
                    ENDPOINT_PAYMENTS,
                    HttpMethod.POST,
                    new HttpEntity<>(
                            new PaymentDto.StartPaymentRequest(orderId, com.loopers.domain.payment.CardType.SAMSUNG, "1234-5678-1234-5678"),
                            authHeaders()
                    ),
                    new ParameterizedTypeReference<>() {
                    }
            );

            assertThat(secondStart.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            assertThat(secondStart.getBody()).isNotNull();
            assertThat(secondStart.getBody().data().transactionKey()).isEqualTo(transactionKey);
            assertThat(secondStart.getBody().data().status()).isEqualTo("REQUESTED");
        }

        @Test
        @DisplayName("동일 주문에 대한 결제 취소 중복 요청은 PG 취소를 한 번만 수행하고 기존 상태를 반환한다")
        void cancelPaymentDuplicateRequestReturnsExistingAndDoesNotDuplicatePgCancel() {
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
            String transactionKey = started.getBody().data().transactionKey();

            fakePaymentGateway.setForceCancelRecoveryRequired(false);
            fakePaymentGateway.setStatus(transactionKey, PaymentStatus.SUCCEEDED, null);

            ResponseEntity<ApiResponse<Void>> callback = testRestTemplate.exchange(
                    ENDPOINT_PAYMENTS + "/callback",
                    HttpMethod.POST,
                    new HttpEntity<>(new PaymentCallbackDto.CallbackRequest(TEST_LOGIN_ID, transactionKey), callbackHeaders()),
                    new ParameterizedTypeReference<>() {
                    }
            );
            assertThat(callback.getStatusCode()).isEqualTo(HttpStatus.OK);

            ResponseEntity<ApiResponse<PaymentDto.PaymentResponse>> firstCancel = testRestTemplate.exchange(
                    ENDPOINT_PAYMENTS + "/" + orderId + "/cancel",
                    HttpMethod.PATCH,
                    new HttpEntity<>(authHeaders()),
                    new ParameterizedTypeReference<>() {
                    }
            );

            assertThat(firstCancel.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(firstCancel.getBody().data().status()).isEqualTo("CANCEL_REQUESTED");
            assertThat(fakePaymentGateway.cancelCallCount()).isEqualTo(1);

            ResponseEntity<ApiResponse<PaymentDto.PaymentResponse>> secondCancel = testRestTemplate.exchange(
                    ENDPOINT_PAYMENTS + "/" + orderId + "/cancel",
                    HttpMethod.PATCH,
                    new HttpEntity<>(authHeaders()),
                    new ParameterizedTypeReference<>() {
                    }
            );

            assertThat(secondCancel.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(secondCancel.getBody().data().status()).isEqualTo("CANCEL_REQUESTED");
            assertThat(fakePaymentGateway.cancelCallCount()).isEqualTo(1);
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

    private UUID issueCouponToTestMember(String couponName, int discountValue) {
        Coupon coupon = couponAdminApplicationService.create(new CreateCouponCommand(
                couponName,
                CouponType.FIXED,
                discountValue,
                0,
                LocalDateTime.now().plusDays(3)
        ));
        couponApplicationService.issue(coupon.id(), TEST_LOGIN_ID);
        return coupon.id();
    }

    private String getLatestPaymentStatus(UUID orderId) {
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
        return queried.getBody().data().payments().get(0).status();
    }

    private void awaitCancelCallCountAtLeast(int expectedCount) {
        for (int i = 0; i < 50; i++) {
            if (fakePaymentGateway.cancelCallCount() >= expectedCount) {
                return;
            }
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new AssertionError("취소 호출 대기 중 인터럽트가 발생했습니다.", e);
            }
        }
        throw new AssertionError("PG 취소 호출 횟수가 기대값에 도달하지 못했습니다. expected="
                + expectedCount + ", actual=" + fakePaymentGateway.cancelCallCount());
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
        private final AtomicInteger cancelCallCount = new AtomicInteger();
        private final Map<String, PaymentGatewayTransaction> byTransactionKey = new ConcurrentHashMap<>();
        private final Map<String, String> orderToTransaction = new ConcurrentHashMap<>();
        private volatile boolean forceCancelRecoveryRequired = true;
        private volatile boolean forceCancelFailedOnce = false;
        private volatile String forceCancelFailedReason = "PG 취소 실패";
        private volatile boolean forceRequestRecoveryRequired = false;

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
            if (forceRequestRecoveryRequired) {
                forceRequestRecoveryRequired = false;
                throw new PaymentRecoveryRequiredException("결제 요청 결과가 불명확하며 상태 조회에도 실패했습니다.");
            }
            return transaction;
        }

        @Override
        public PaymentGatewayTransaction cancelPayment(PaymentGatewayCancelRequest request) {
            cancelCallCount.incrementAndGet();
            PaymentGatewayTransaction found = byTransactionKey.get(request.transactionKey());
            if (found == null) {
                throw new com.loopers.support.error.CoreException(com.loopers.support.error.ErrorType.NOT_FOUND, "결제건이 없습니다.");
            }
            if (forceCancelFailedOnce) {
                forceCancelFailedOnce = false;
                PaymentGatewayTransaction next = new PaymentGatewayTransaction(
                        found.transactionKey(),
                        found.orderReference(),
                        PaymentStatus.CANCEL_FAILED,
                        forceCancelFailedReason
                );
                byTransactionKey.put(found.transactionKey(), next);
                return next;
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
            cancelCallCount.set(0);
            forceCancelRecoveryRequired = true;
            forceCancelFailedOnce = false;
            forceCancelFailedReason = "PG 취소 실패";
            forceRequestRecoveryRequired = false;
        }

        int cancelCallCount() {
            return cancelCallCount.get();
        }

        void setForceCancelRecoveryRequired(boolean forceCancelRecoveryRequired) {
            this.forceCancelRecoveryRequired = forceCancelRecoveryRequired;
        }

        void forceNextCancelFailed(String reason) {
            this.forceCancelFailedOnce = true;
            this.forceCancelFailedReason = reason;
        }

        void forceNextRequestRecoveryRequired() {
            this.forceRequestRecoveryRequired = true;
        }

        String getTransactionKeyByOrderReference(String orderReference) {
            return orderToTransaction.get(orderReference);
        }
    }
}
