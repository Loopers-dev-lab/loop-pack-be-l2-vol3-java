package com.loopers.interfaces.api.payment.v1;

import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static com.loopers.interfaces.api.order.v1.OrderSteps.createOrder;
import static com.loopers.interfaces.api.payment.v1.PaymentSteps.createPayment;
import static com.loopers.interfaces.api.payment.v1.PaymentSteps.handlePaymentCallback;
import static com.loopers.interfaces.api.user.v1.UserSteps.signUp;
import static com.loopers.support.E2ETestHelper.assertErrorResponse;
import static com.loopers.support.E2ETestHelper.userAuthHeaders;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.loopers.domain.order.Order;
import com.loopers.domain.order.OrderRepository;
import com.loopers.domain.order.OrderStatus;
import com.loopers.domain.payment.Payment;
import com.loopers.domain.payment.PaymentRepository;
import com.loopers.domain.payment.PaymentStatus;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductRepository;
import com.loopers.infrastructure.payment.pg.PgPaymentHttpInterface;
import com.loopers.interfaces.api.brand.v1.BrandDto;
import com.loopers.interfaces.api.brand.v1.BrandSteps;
import com.loopers.interfaces.api.order.v1.OrderDto;
import com.loopers.interfaces.api.product.v1.ProductDto;
import com.loopers.interfaces.api.product.v1.ProductSteps;
import com.loopers.interfaces.api.user.v1.UserV1Dto;
import com.loopers.support.BaseE2ETest;
import com.loopers.support.error.ErrorType;

@Import(PaymentV1ApiE2ETest.WireMockPgClientConfig.class)
class PaymentV1ApiE2ETest extends BaseE2ETest {

    /**
     * WireMock과 호환되는 {@link PgPaymentHttpInterface} 빈을 제공한다.
     *
     * <p>기본 {@code PgPaymentClientConfig}는 {@code JdkClientHttpRequestFactory}를 사용하는데,
     * {@code HttpServiceProxyFactory} 프록시를 통해 WireMock에 요청하면 EOF 에러가 발생한다. {@code SimpleClientHttpRequestFactory}(HTTP/1.1)로 교체하여 이 문제를 우회한다.
     *
     * <p>{@code @TestConfiguration}은 내부 클래스여도 자동 로드되지 않으므로,
     * 반드시 {@code @Import}로 명시적으로 등록해야 한다.
     */
    @TestConfiguration
    static class WireMockPgClientConfig {

        /**
         * WireMock URL을 가리키는 {@link PgPaymentHttpInterface} 빈을 생성한다.
         *
         * <p>{@code @Primary}로 선언하여 기본 {@code PgPaymentClientConfig}의 빈을 대체한다.
         *
         * @param baseUrl        WireMock URL ({@code @DynamicPropertySource}로 주입)
         * @param connectTimeout 연결 타임아웃 (ms)
         * @param readTimeout    읽기 타임아웃 (ms)
         * @return WireMock과 통신하는 PG 클라이언트 프록시
         */
        @Bean
        @Primary
        PgPaymentHttpInterface wireMockPgPaymentHttpInterface(
                @Value("${pg.base-url}") String baseUrl,
                @Value("${pg.connect-timeout}") int connectTimeout,
                @Value("${pg.read-timeout}") int readTimeout
        ) {
            var factory = new SimpleClientHttpRequestFactory();
            factory.setConnectTimeout(Duration.ofMillis(connectTimeout));
            factory.setReadTimeout(Duration.ofMillis(readTimeout));

            RestClient restClient = RestClient.builder()
                    .baseUrl(baseUrl)
                    .requestFactory(factory)
                    .build();
            return HttpServiceProxyFactory
                    .builderFor(RestClientAdapter.create(restClient))
                    .build()
                    .createClient(PgPaymentHttpInterface.class);
        }
    }

    @RegisterExtension
    static WireMockExtension wireMock = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort())
            .build();

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("pg.base-url", wireMock::baseUrl);
    }

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private ProductRepository productRepository;

    private HttpHeaders userHeaders;
    private String orderKey;
    private Long productId;

    private static final String PG_PAYMENT_URL = "/api/v1/payments";
    private static final long INITIAL_STOCK = 100L;
    private static final long ORDER_QUANTITY = 2L;

    @BeforeEach
    void setUp() {
        wireMock.resetAll();

        var loginId = "testuser1";
        var loginPw = "Password1!";
        signUp(testRestTemplate, new UserV1Dto.SignUpRequest(loginId, loginPw, "테스트", "2000-01-01", "test@test.com"));
        userHeaders = userAuthHeaders(loginId, loginPw);

        var brandId = BrandSteps.createBrand(
                testRestTemplate,
                new BrandDto.CreateBrandRequest("테스트 브랜드", "https://example.com/logo.png", "브랜드 설명")
        );
        productId = ProductSteps.createProduct(
                testRestTemplate,
                new ProductDto.CreateProductRequest(brandId, "테스트 상품", "https://example.com/thumb.png", 10000L, INITIAL_STOCK, "상품 설명")
        );

        var orderResponse = createOrder(
                testRestTemplate,
                new OrderDto.CreateOrderRequest(List.of(new OrderDto.OrderItemRequest(productId, ORDER_QUANTITY)), null),
                userHeaders
        );
        orderKey = orderResponse.getBody().data().orderKey();
    }

    @DisplayName("결제 생성: PG가 정상 응답하면, 결제가 PENDING 상태로 생성된다.")
    @Test
    void createPayment_createsPayment_whenPgRespondsSuccessfully() {
        // arrange
        stubPgSuccess("txn-test-001");
        var request = paymentRequest();

        // act
        var response = createPayment(testRestTemplate, request, userHeaders);

        // assert
        assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED),
                () -> assertThat(response.getBody().data().status()).isEqualTo(PaymentStatus.PENDING),
                () -> assertThat(response.getBody().data().transactionKey()).isEqualTo("txn-test-001"),
                () -> assertThat(response.getBody().data().paymentId()).isNotNull()
        );

        wireMock.verify(postRequestedFor(urlEqualTo(PG_PAYMENT_URL))
                .withHeader("X-USER-ID", equalTo("1")));
    }

    @DisplayName("결제 생성: 존재하지 않는 주문이면, 404를 반환한다.")
    @Test
    void createPayment_returnsNotFound_whenOrderDoesNotExist() {
        // arrange
        var request = new PaymentDto.CreatePaymentRequest(
                "non-existent-key",
                "SAMSUNG",
                "1234-5678-9012-3456",
                "https://example.com/callback"
        );

        // act
        var response = createPayment(testRestTemplate, request, userHeaders);

        // assert
        assertErrorResponse(response, HttpStatus.NOT_FOUND, ErrorType.ORDER_NOT_FOUND);
    }

    @DisplayName("결제 생성: 결제 불가능한 주문 상태이면, 400을 반환한다.")
    @Test
    void createPayment_returnsBadRequest_whenOrderNotPayable() {
        // arrange
        stubPgSuccess("txn-paid-001");
        createPayment(testRestTemplate, paymentRequest(), userHeaders);
        handlePaymentCallback(testRestTemplate,
                new PaymentDto.PaymentCallbackRequest("txn-paid-001", PaymentStatus.SUCCESS, null));

        // act
        stubPgSuccess("txn-paid-002");
        var response = createPayment(testRestTemplate, paymentRequest(), userHeaders);

        // assert
        assertErrorResponse(response, HttpStatus.BAD_REQUEST, ErrorType.ORDER_NOT_PAYABLE);
    }

    @DisplayName("결제 생성: PG가 400 에러를 반환하면, 400을 반환한다.")
    @Test
    void createPayment_returnsBadRequest_whenPgReturns400() {
        // arrange
        wireMock.stubFor(post(urlEqualTo(PG_PAYMENT_URL))
                .willReturn(WireMock.aResponse()
                        .withStatus(400)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"meta":{"result":"FAIL","errorCode":"INVALID_CARD","message":"유효하지 않은 카드"},"data":null}""")));
        var request = paymentRequest();

        // act
        var response = createPayment(testRestTemplate, request, userHeaders);

        // assert
        assertErrorResponse(response, HttpStatus.BAD_REQUEST, ErrorType.BAD_REQUEST);
    }

    @DisplayName("결제 생성: PG가 500 에러를 반환하면, 503을 반환한다.")
    @Test
    void createPayment_returnsServiceUnavailable_whenPgReturns500() {
        // arrange
        wireMock.stubFor(post(urlEqualTo(PG_PAYMENT_URL))
                .willReturn(WireMock.aResponse()
                        .withStatus(500)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"meta":{"result":"FAIL","errorCode":"INTERNAL_ERROR","message":"서버 오류"},"data":null}""")));
        var request = paymentRequest();

        // act
        var response = createPayment(testRestTemplate, request, userHeaders);

        // assert
        assertErrorResponse(response, HttpStatus.SERVICE_UNAVAILABLE, ErrorType.PAYMENT_GATEWAY_UNAVAILABLE);
    }

    @DisplayName("콜백: 결제 성공 콜백이 오면, Payment는 SUCCESS, Order는 PAID가 된다.")
    @Test
    void callback_updatesToSuccess_whenSuccessCallback() {
        // arrange
        stubPgSuccess("txn-success-001");
        createPayment(testRestTemplate, paymentRequest(), userHeaders);

        // act
        var response = handlePaymentCallback(testRestTemplate,
                new PaymentDto.PaymentCallbackRequest("txn-success-001", PaymentStatus.SUCCESS, null));

        // assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

        Payment payment = paymentRepository.findByTransactionKey("txn-success-001").orElseThrow();
        Order order = orderRepository.findByOrderKeyWithItems(orderKey).orElseThrow();
        assertAll(
                () -> assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SUCCESS),
                () -> assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID)
        );
    }

    @DisplayName("콜백: 결제 실패 콜백이 오면, Payment는 FAILED, Order는 FAILED가 되고 재고가 복원된다.")
    @Test
    void callback_updatesToFailed_whenFailureCallback() {
        // arrange
        stubPgSuccess("txn-fail-001");
        createPayment(testRestTemplate, paymentRequest(), userHeaders);

        // act
        var response = handlePaymentCallback(testRestTemplate,
                new PaymentDto.PaymentCallbackRequest("txn-fail-001", PaymentStatus.FAILED, "잔액 부족"));

        // assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

        Payment payment = paymentRepository.findByTransactionKey("txn-fail-001").orElseThrow();
        Order order = orderRepository.findByOrderKeyWithItems(orderKey).orElseThrow();
        Product product = productRepository.findById(productId).orElseThrow();
        assertAll(
                () -> assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED),
                () -> assertThat(payment.getReason()).isEqualTo("잔액 부족"),
                () -> assertThat(order.getStatus()).isEqualTo(OrderStatus.FAILED),
                () -> assertThat(product.getStock().getValue()).isEqualTo(INITIAL_STOCK)
        );
    }

    @DisplayName("콜백: 이미 처리된 결제에 중복 콜백이 오면, 상태가 변경되지 않는다.")
    @Test
    void callback_remainsUnchanged_whenDuplicateCallback() {
        // arrange
        stubPgSuccess("txn-dup-001");
        createPayment(testRestTemplate, paymentRequest(), userHeaders);
        handlePaymentCallback(testRestTemplate, new PaymentDto.PaymentCallbackRequest("txn-dup-001", PaymentStatus.SUCCESS, null));

        // act
        var response = handlePaymentCallback(
                testRestTemplate,
                new PaymentDto.PaymentCallbackRequest("txn-dup-001", PaymentStatus.FAILED, "중복 처리 시도")
        );

        // assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

        Payment payment = paymentRepository.findByTransactionKey("txn-dup-001").orElseThrow();
        Order order = orderRepository.findByOrderKeyWithItems(orderKey).orElseThrow();
        assertAll(
                () -> assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SUCCESS),
                () -> assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID)
        );
    }

    private void stubPgSuccess(String transactionKey) {
        String body = """
                {"meta":{"result":"SUCCESS","errorCode":null,"message":null},"data":{"transactionKey":"%s","status":"PENDING","reason":null}}""".formatted(transactionKey);
        wireMock.stubFor(post(urlEqualTo(PG_PAYMENT_URL))
                .willReturn(WireMock.aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(body)));
    }

    private PaymentDto.CreatePaymentRequest paymentRequest() {
        return new PaymentDto.CreatePaymentRequest(orderKey, "SAMSUNG", "1234-5678-9012-3456", "https://example.com/callback");
    }
}
