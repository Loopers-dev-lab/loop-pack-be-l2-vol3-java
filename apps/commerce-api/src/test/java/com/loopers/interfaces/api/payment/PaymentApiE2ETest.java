package com.loopers.interfaces.api.payment;

import com.loopers.application.order.OrderService;
import com.loopers.domain.payment.CardType;
import com.loopers.domain.payment.Payment;
import com.loopers.domain.payment.PaymentStatus;
import com.loopers.domain.payment.gateway.PgType;
import com.loopers.application.payment.PaymentCommand;
import com.loopers.application.payment.PaymentService;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.order.OrderRequest;
import com.loopers.support.E2ETestFixture;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(E2ETestFixture.class)
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class PaymentApiE2ETest {

    private static final String PAYMENT_ENDPOINT = "/api/v1/payments";
    private static final String LOGIN_ID = "testuser";
    private static final String PASSWORD = "Test1234!";

    @Autowired
    private TestRestTemplate testRestTemplate;

    @Autowired
    private E2ETestFixture fixture;

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private OrderService orderService;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @Value("${payment.toss.base-url}")
    private String tossBaseUrl;

    @Value("${payment.nice.base-url}")
    private String niceBaseUrl;

    private final RestTemplate mockPgClient = new RestTemplate();

    @BeforeEach
    void setUp() {
        databaseCleanUp.truncateAllTables();
        mockPgClient.delete(tossBaseUrl + "/test/reset");
        mockPgClient.delete(niceBaseUrl + "/test/reset");
    }

    @Nested
    class 결제_요청 {

        @Test
        void 주문이_존재하지_않으면_404_응답() {
            fixture.signUp(LOGIN_ID, PASSWORD, "홍길동", "test@example.com");

            PaymentRequest.Request request = new PaymentRequest.Request(
                    999L, CardType.SAMSUNG, "1234-5678-9012-3456", PgType.TOSS);

            ResponseEntity<ApiResponse<PaymentV1Dto.PaymentResponse>> response = postPayment(request);

            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND),
                    () -> assertThat(response.getBody().meta().message()).contains("존재하지 않는 주문입니다")
            );
        }

        @Test
        void 본인의_주문이_아니면_404_응답() {
            fixture.signUp(LOGIN_ID, PASSWORD, "홍길동", "test@example.com");
            fixture.signUp("otheruser", "Other1234!", "김철수", "other@example.com");

            Long brandId = fixture.registerBrand("나이키", "스포츠 브랜드");
            Long productId = fixture.registerProduct(brandId, "운동화", new BigDecimal("50000"), 100, "편한 운동화");
            Long orderId = fixture.placeOrder(
                    List.of(new OrderRequest.PlaceItem(productId, 1)),
                    "otheruser", "Other1234!");

            PaymentRequest.Request request = new PaymentRequest.Request(
                    orderId, CardType.SAMSUNG, "1234-5678-9012-3456", PgType.TOSS);

            ResponseEntity<ApiResponse<PaymentV1Dto.PaymentResponse>> response = postPayment(request);

            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND),
                    () -> assertThat(response.getBody().meta().message()).contains("존재하지 않는 주문입니다")
            );
        }

        @Test
        void 이미_활성_결제가_있으면_409_응답() {
            fixture.signUp(LOGIN_ID, PASSWORD, "홍길동", "test@example.com");
            Long brandId = fixture.registerBrand("나이키", "스포츠 브랜드");
            Long productId = fixture.registerProduct(brandId, "운동화", new BigDecimal("50000"), 100, "편한 운동화");
            Long orderId = fixture.placeOrder(
                    List.of(new OrderRequest.PlaceItem(productId, 1)),
                    LOGIN_ID, PASSWORD);

            // 결제 하나 직접 생성 (REQUESTED 상태)
            paymentService.createPayment(PaymentCommand.Create.of(orderId, 1L, PgType.TOSS, CardType.SAMSUNG, "1234-5678-9012-3456", new BigDecimal("50000")));

            PaymentRequest.Request request = new PaymentRequest.Request(
                    orderId, CardType.SAMSUNG, "9999-8888-7777-6666", PgType.TOSS);

            ResponseEntity<ApiResponse<PaymentV1Dto.PaymentResponse>> response = postPayment(request);

            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT),
                    () -> assertThat(response.getBody().meta().message()).contains("이미 결제가 진행 중이거나 완료된 주문입니다")
            );
        }

        @Test
        void 주문_상태가_CREATED가_아니면_400_응답() {
            fixture.signUp(LOGIN_ID, PASSWORD, "홍길동", "test@example.com");
            Long brandId = fixture.registerBrand("나이키", "스포츠 브랜드");
            Long productId = fixture.registerProduct(brandId, "운동화", new BigDecimal("50000"), 100, "편한 운동화");
            Long orderId = fixture.placeOrder(
                    List.of(new OrderRequest.PlaceItem(productId, 1)),
                    LOGIN_ID, PASSWORD);

            orderService.payOrder(orderId);

            PaymentRequest.Request request = new PaymentRequest.Request(
                    orderId, CardType.SAMSUNG, "1234-5678-9012-3456", PgType.TOSS);

            ResponseEntity<ApiResponse<PaymentV1Dto.PaymentResponse>> response = postPayment(request);

            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST),
                    () -> assertThat(response.getBody().meta().message()).contains("결제할 수 없는 주문 상태입니다")
            );
        }

        @Test
        void 요청_필드_규칙_위반_시_400_응답() {
            fixture.signUp(LOGIN_ID, PASSWORD, "홍길동", "test@example.com");

            PaymentRequest.Request request = new PaymentRequest.Request(
                    null, null, "invalid-card", null);

            ResponseEntity<ApiResponse<PaymentV1Dto.PaymentResponse>> response = postPayment(request);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @Test
        void 인증_헤더가_누락되면_401_응답() {
            PaymentRequest.Request request = new PaymentRequest.Request(
                    1L, CardType.SAMSUNG, "1234-5678-9012-3456", PgType.TOSS);

            ResponseEntity<ApiResponse<PaymentV1Dto.PaymentResponse>> response = testRestTemplate.exchange(
                    PAYMENT_ENDPOINT, HttpMethod.POST,
                    new HttpEntity<>(request),
                    new ParameterizedTypeReference<>() {}
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }

        @Test
        void 인증에_실패하면_401_응답() {
            PaymentRequest.Request request = new PaymentRequest.Request(
                    1L, CardType.SAMSUNG, "1234-5678-9012-3456", PgType.TOSS);

            HttpHeaders headers = new HttpHeaders();
            headers.set("X-Loopers-LoginId", "wronguser");
            headers.set("X-Loopers-LoginPw", "wrongpw");

            ResponseEntity<ApiResponse<PaymentV1Dto.PaymentResponse>> response = testRestTemplate.exchange(
                    PAYMENT_ENDPOINT, HttpMethod.POST,
                    new HttpEntity<>(request, headers),
                    new ParameterizedTypeReference<>() {}
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }
    }

    @Nested
    class 결제_상세_조회 {

        @Test
        void 본인의_결제를_조회하면_200_응답과_결제_정보를_반환한다() {
            fixture.signUp(LOGIN_ID, PASSWORD, "홍길동", "test@example.com");
            Long brandId = fixture.registerBrand("나이키", "스포츠 브랜드");
            Long productId = fixture.registerProduct(brandId, "운동화", new BigDecimal("50000"), 100, "편한 운동화");
            Long orderId = fixture.placeOrder(
                    List.of(new OrderRequest.PlaceItem(productId, 1)),
                    LOGIN_ID, PASSWORD);

            Payment payment = fixture.createRequestedPayment(orderId, 1L, new BigDecimal("50000"));

            ResponseEntity<ApiResponse<PaymentV1Dto.PaymentResponse>> response = testRestTemplate.exchange(
                    PAYMENT_ENDPOINT + "/" + payment.getId(), HttpMethod.GET,
                    new HttpEntity<>(fixture.userHeaders(LOGIN_ID, PASSWORD)),
                    new ParameterizedTypeReference<>() {}
            );

            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(response.getBody().data().id()).isEqualTo(payment.getId()),
                    () -> assertThat(response.getBody().data().status()).isEqualTo(PaymentStatus.REQUESTED),
                    () -> assertThat(response.getBody().data().paymentKey()).isNotNull(),
                    () -> assertThat(response.getBody().data().amount()).isEqualByComparingTo(new BigDecimal("50000"))
            );
        }

        @Test
        void 존재하지_않는_결제이면_404_응답() {
            fixture.signUp(LOGIN_ID, PASSWORD, "홍길동", "test@example.com");

            ResponseEntity<ApiResponse<PaymentV1Dto.PaymentResponse>> response = testRestTemplate.exchange(
                    PAYMENT_ENDPOINT + "/999", HttpMethod.GET,
                    new HttpEntity<>(fixture.userHeaders(LOGIN_ID, PASSWORD)),
                    new ParameterizedTypeReference<>() {}
            );

            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND),
                    () -> assertThat(response.getBody().meta().message()).contains("존재하지 않는 결제입니다")
            );
        }

        @Test
        void 본인의_결제가_아니면_404_응답() {
            fixture.signUp(LOGIN_ID, PASSWORD, "홍길동", "test@example.com");
            fixture.signUp("otheruser", "Other1234!", "김철수", "other@example.com");

            Long brandId = fixture.registerBrand("나이키", "스포츠 브랜드");
            Long productId = fixture.registerProduct(brandId, "운동화", new BigDecimal("50000"), 100, "편한 운동화");
            Long orderId = fixture.placeOrder(
                    List.of(new OrderRequest.PlaceItem(productId, 1)),
                    "otheruser", "Other1234!");

            Payment payment = fixture.createRequestedPayment(orderId, 2L, new BigDecimal("50000"));

            ResponseEntity<ApiResponse<PaymentV1Dto.PaymentResponse>> response = testRestTemplate.exchange(
                    PAYMENT_ENDPOINT + "/" + payment.getId(), HttpMethod.GET,
                    new HttpEntity<>(fixture.userHeaders(LOGIN_ID, PASSWORD)),
                    new ParameterizedTypeReference<>() {}
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }

        @Test
        void 인증_헤더가_누락되면_401_응답() {
            ResponseEntity<ApiResponse<PaymentV1Dto.PaymentResponse>> response = testRestTemplate.exchange(
                    PAYMENT_ENDPOINT + "/1", HttpMethod.GET,
                    new HttpEntity<>(null),
                    new ParameterizedTypeReference<>() {}
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }

        @Test
        void 인증에_실패하면_401_응답() {
            HttpHeaders headers = new HttpHeaders();
            headers.set("X-Loopers-LoginId", "wronguser");
            headers.set("X-Loopers-LoginPw", "wrongpw");

            ResponseEntity<ApiResponse<PaymentV1Dto.PaymentResponse>> response = testRestTemplate.exchange(
                    PAYMENT_ENDPOINT + "/1", HttpMethod.GET,
                    new HttpEntity<>(headers),
                    new ParameterizedTypeReference<>() {}
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }
    }

    @Nested
    class 결제_취소 {

        @Test
        void SUCCEEDED_결제를_취소하면_CANCELED_상태로_변경된다() {
            fixture.signUp(LOGIN_ID, PASSWORD, "홍길동", "test@example.com");
            Long brandId = fixture.registerBrand("나이키", "스포츠 브랜드");
            Long productId = fixture.registerProduct(brandId, "운동화", new BigDecimal("50000"), 100, "편한 운동화");
            Long orderId = fixture.placeOrder(
                    List.of(new OrderRequest.PlaceItem(productId, 1)),
                    LOGIN_ID, PASSWORD);

            Payment payment = fixture.createSucceededPayment(orderId, 1L, new BigDecimal("50000"));

            PaymentRequest.Cancel cancelRequest = new PaymentRequest.Cancel("단순 변심", null);

            ResponseEntity<ApiResponse<PaymentV1Dto.PaymentResponse>> response = testRestTemplate.exchange(
                    PAYMENT_ENDPOINT + "/" + payment.getId() + "/cancel", HttpMethod.POST,
                    new HttpEntity<>(cancelRequest, fixture.userHeaders(LOGIN_ID, PASSWORD)),
                    new ParameterizedTypeReference<>() {}
            );

            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(response.getBody().data().status()).isEqualTo(PaymentStatus.CANCELED),
                    () -> assertThat(response.getBody().data().cancelReason()).isEqualTo("단순 변심")
            );
        }

        @Test
        void REQUESTED_결제를_취소하면_400_응답() {
            fixture.signUp(LOGIN_ID, PASSWORD, "홍길동", "test@example.com");
            Long brandId = fixture.registerBrand("나이키", "스포츠 브랜드");
            Long productId = fixture.registerProduct(brandId, "운동화", new BigDecimal("50000"), 100, "편한 운동화");
            Long orderId = fixture.placeOrder(
                    List.of(new OrderRequest.PlaceItem(productId, 1)),
                    LOGIN_ID, PASSWORD);

            Payment payment = fixture.createRequestedPayment(orderId, 1L, new BigDecimal("50000"));

            PaymentRequest.Cancel cancelRequest = new PaymentRequest.Cancel("변심", null);

            ResponseEntity<ApiResponse<PaymentV1Dto.PaymentResponse>> response = testRestTemplate.exchange(
                    PAYMENT_ENDPOINT + "/" + payment.getId() + "/cancel", HttpMethod.POST,
                    new HttpEntity<>(cancelRequest, fixture.userHeaders(LOGIN_ID, PASSWORD)),
                    new ParameterizedTypeReference<>() {}
            );

            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST),
                    () -> assertThat(response.getBody().meta().message()).contains("취소할 수 없는 결제 상태입니다")
            );
        }

        @Test
        void 본인의_결제가_아니면_404_응답() {
            fixture.signUp(LOGIN_ID, PASSWORD, "홍길동", "test@example.com");
            fixture.signUp("otheruser", "Other1234!", "김철수", "other@example.com");

            Long brandId = fixture.registerBrand("나이키", "스포츠 브랜드");
            Long productId = fixture.registerProduct(brandId, "운동화", new BigDecimal("50000"), 100, "편한 운동화");
            Long orderId = fixture.placeOrder(
                    List.of(new OrderRequest.PlaceItem(productId, 1)),
                    "otheruser", "Other1234!");

            Payment payment = fixture.createSucceededPayment(orderId, 2L, new BigDecimal("50000"));

            PaymentRequest.Cancel cancelRequest = new PaymentRequest.Cancel("변심", null);

            ResponseEntity<ApiResponse<PaymentV1Dto.PaymentResponse>> response = testRestTemplate.exchange(
                    PAYMENT_ENDPOINT + "/" + payment.getId() + "/cancel", HttpMethod.POST,
                    new HttpEntity<>(cancelRequest, fixture.userHeaders(LOGIN_ID, PASSWORD)),
                    new ParameterizedTypeReference<>() {}
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }

        @Test
        void 존재하지_않는_결제이면_404_응답() {
            fixture.signUp(LOGIN_ID, PASSWORD, "홍길동", "test@example.com");

            PaymentRequest.Cancel cancelRequest = new PaymentRequest.Cancel("변심", null);

            ResponseEntity<ApiResponse<PaymentV1Dto.PaymentResponse>> response = testRestTemplate.exchange(
                    PAYMENT_ENDPOINT + "/999/cancel", HttpMethod.POST,
                    new HttpEntity<>(cancelRequest, fixture.userHeaders(LOGIN_ID, PASSWORD)),
                    new ParameterizedTypeReference<>() {}
            );

            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND),
                    () -> assertThat(response.getBody().meta().message()).contains("존재하지 않는 결제입니다")
            );
        }

        @Test
        void 인증_헤더가_누락되면_401_응답() {
            PaymentRequest.Cancel cancelRequest = new PaymentRequest.Cancel("변심", null);

            ResponseEntity<ApiResponse<PaymentV1Dto.PaymentResponse>> response = testRestTemplate.exchange(
                    PAYMENT_ENDPOINT + "/1/cancel", HttpMethod.POST,
                    new HttpEntity<>(cancelRequest),
                    new ParameterizedTypeReference<>() {}
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }

        @Test
        void 인증에_실패하면_401_응답() {
            PaymentRequest.Cancel cancelRequest = new PaymentRequest.Cancel("변심", null);

            HttpHeaders headers = new HttpHeaders();
            headers.set("X-Loopers-LoginId", "wronguser");
            headers.set("X-Loopers-LoginPw", "wrongpw");

            ResponseEntity<ApiResponse<PaymentV1Dto.PaymentResponse>> response = testRestTemplate.exchange(
                    PAYMENT_ENDPOINT + "/1/cancel", HttpMethod.POST,
                    new HttpEntity<>(cancelRequest, headers),
                    new ParameterizedTypeReference<>() {}
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }
    }

    @Nested
    class 주문별_결제_조회 {

        private static final String ORDER_PAYMENT_ENDPOINT = "/api/v1/orders";

        @Test
        void 결제가_있는_주문을_조회하면_200_응답과_결제_정보를_반환한다() {
            fixture.signUp(LOGIN_ID, PASSWORD, "홍길동", "test@example.com");
            Long brandId = fixture.registerBrand("나이키", "스포츠 브랜드");
            Long productId = fixture.registerProduct(brandId, "운동화", new BigDecimal("50000"), 100, "편한 운동화");
            Long orderId = fixture.placeOrder(
                    List.of(new OrderRequest.PlaceItem(productId, 1)),
                    LOGIN_ID, PASSWORD);

            Payment payment = fixture.createRequestedPayment(orderId, 1L, new BigDecimal("50000"));

            ResponseEntity<ApiResponse<PaymentV1Dto.PaymentResponse>> response = testRestTemplate.exchange(
                    ORDER_PAYMENT_ENDPOINT + "/" + orderId + "/payment", HttpMethod.GET,
                    new HttpEntity<>(fixture.userHeaders(LOGIN_ID, PASSWORD)),
                    new ParameterizedTypeReference<>() {}
            );

            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(response.getBody().data().id()).isEqualTo(payment.getId()),
                    () -> assertThat(response.getBody().data().orderId()).isEqualTo(orderId),
                    () -> assertThat(response.getBody().data().status()).isEqualTo(PaymentStatus.REQUESTED)
            );
        }

        @Test
        void 결제가_없는_주문을_조회하면_200_응답과_빈_결제_정보를_반환한다() {
            fixture.signUp(LOGIN_ID, PASSWORD, "홍길동", "test@example.com");
            Long brandId = fixture.registerBrand("나이키", "스포츠 브랜드");
            Long productId = fixture.registerProduct(brandId, "운동화", new BigDecimal("50000"), 100, "편한 운동화");
            Long orderId = fixture.placeOrder(
                    List.of(new OrderRequest.PlaceItem(productId, 1)),
                    LOGIN_ID, PASSWORD);

            ResponseEntity<ApiResponse<PaymentV1Dto.PaymentResponse>> response = testRestTemplate.exchange(
                    ORDER_PAYMENT_ENDPOINT + "/" + orderId + "/payment", HttpMethod.GET,
                    new HttpEntity<>(fixture.userHeaders(LOGIN_ID, PASSWORD)),
                    new ParameterizedTypeReference<>() {}
            );

            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(response.getBody().data().id()).isNull(),
                    () -> assertThat(response.getBody().data().orderId()).isEqualTo(orderId),
                    () -> assertThat(response.getBody().data().status()).isNull()
            );
        }

        @Test
        void 주문이_존재하지_않으면_404_응답() {
            fixture.signUp(LOGIN_ID, PASSWORD, "홍길동", "test@example.com");

            ResponseEntity<ApiResponse<PaymentV1Dto.PaymentResponse>> response = testRestTemplate.exchange(
                    ORDER_PAYMENT_ENDPOINT + "/999/payment", HttpMethod.GET,
                    new HttpEntity<>(fixture.userHeaders(LOGIN_ID, PASSWORD)),
                    new ParameterizedTypeReference<>() {}
            );

            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND),
                    () -> assertThat(response.getBody().meta().message()).contains("존재하지 않는 주문입니다")
            );
        }

        @Test
        void 본인의_주문이_아니면_404_응답() {
            fixture.signUp(LOGIN_ID, PASSWORD, "홍길동", "test@example.com");
            fixture.signUp("otheruser", "Other1234!", "김철수", "other@example.com");

            Long brandId = fixture.registerBrand("나이키", "스포츠 브랜드");
            Long productId = fixture.registerProduct(brandId, "운동화", new BigDecimal("50000"), 100, "편한 운동화");
            Long orderId = fixture.placeOrder(
                    List.of(new OrderRequest.PlaceItem(productId, 1)),
                    "otheruser", "Other1234!");

            ResponseEntity<ApiResponse<PaymentV1Dto.PaymentResponse>> response = testRestTemplate.exchange(
                    ORDER_PAYMENT_ENDPOINT + "/" + orderId + "/payment", HttpMethod.GET,
                    new HttpEntity<>(fixture.userHeaders(LOGIN_ID, PASSWORD)),
                    new ParameterizedTypeReference<>() {}
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }

        @Test
        void 인증_헤더가_누락되면_401_응답() {
            ResponseEntity<ApiResponse<PaymentV1Dto.PaymentResponse>> response = testRestTemplate.exchange(
                    ORDER_PAYMENT_ENDPOINT + "/1/payment", HttpMethod.GET,
                    new HttpEntity<>(null),
                    new ParameterizedTypeReference<>() {}
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }

        @Test
        void 인증에_실패하면_401_응답() {
            HttpHeaders headers = new HttpHeaders();
            headers.set("X-Loopers-LoginId", "wronguser");
            headers.set("X-Loopers-LoginPw", "wrongpw");

            ResponseEntity<ApiResponse<PaymentV1Dto.PaymentResponse>> response = testRestTemplate.exchange(
                    ORDER_PAYMENT_ENDPOINT + "/1/payment", HttpMethod.GET,
                    new HttpEntity<>(headers),
                    new ParameterizedTypeReference<>() {}
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }
    }

    @Nested
    class 결제_수동_확인 {

        @Test
        void REQUESTED_상태이면_결제_미완료로_FAILED_처리되고_200_응답() {
            fixture.signUp(LOGIN_ID, PASSWORD, "홍길동", "test@example.com");
            Long brandId = fixture.registerBrand("나이키", "스포츠 브랜드");
            Long productId = fixture.registerProduct(brandId, "운동화", new BigDecimal("50000"), 100, "편한 운동화");
            Long orderId = fixture.placeOrder(
                    List.of(new OrderRequest.PlaceItem(productId, 1)),
                    LOGIN_ID, PASSWORD);

            Payment payment = fixture.createRequestedPayment(orderId, 1L, new BigDecimal("50000"));

            ResponseEntity<ApiResponse<PaymentV1Dto.PaymentResponse>> response = testRestTemplate.exchange(
                    PAYMENT_ENDPOINT + "/" + payment.getId() + "/verify", HttpMethod.POST,
                    new HttpEntity<>(fixture.userHeaders(LOGIN_ID, PASSWORD)),
                    new ParameterizedTypeReference<>() {}
            );

            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(response.getBody().data().status()).isEqualTo(PaymentStatus.FAILED)
            );
        }

        @Test
        void 이미_확정된_결제이면_400_응답() {
            fixture.signUp(LOGIN_ID, PASSWORD, "홍길동", "test@example.com");
            Long brandId = fixture.registerBrand("나이키", "스포츠 브랜드");
            Long productId = fixture.registerProduct(brandId, "운동화", new BigDecimal("50000"), 100, "편한 운동화");
            Long orderId = fixture.placeOrder(
                    List.of(new OrderRequest.PlaceItem(productId, 1)),
                    LOGIN_ID, PASSWORD);

            Payment payment = fixture.createRequestedPayment(orderId, 1L, new BigDecimal("50000"));
            paymentService.markFailed(payment.getId(), "이전 실패");

            ResponseEntity<ApiResponse<PaymentV1Dto.PaymentResponse>> response = testRestTemplate.exchange(
                    PAYMENT_ENDPOINT + "/" + payment.getId() + "/verify", HttpMethod.POST,
                    new HttpEntity<>(fixture.userHeaders(LOGIN_ID, PASSWORD)),
                    new ParameterizedTypeReference<>() {}
            );

            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST),
                    () -> assertThat(response.getBody().meta().message()).contains("이미 확정되었거나 취소 진행 중인 결제입니다")
            );
        }

        @Test
        void 존재하지_않는_결제이면_404_응답() {
            fixture.signUp(LOGIN_ID, PASSWORD, "홍길동", "test@example.com");

            ResponseEntity<ApiResponse<PaymentV1Dto.PaymentResponse>> response = testRestTemplate.exchange(
                    PAYMENT_ENDPOINT + "/999/verify", HttpMethod.POST,
                    new HttpEntity<>(fixture.userHeaders(LOGIN_ID, PASSWORD)),
                    new ParameterizedTypeReference<>() {}
            );

            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND),
                    () -> assertThat(response.getBody().meta().message()).contains("존재하지 않는 결제입니다")
            );
        }

        @Test
        void 본인의_결제가_아니면_404_응답() {
            fixture.signUp(LOGIN_ID, PASSWORD, "홍길동", "test@example.com");
            fixture.signUp("otheruser", "Other1234!", "김철수", "other@example.com");

            Long brandId = fixture.registerBrand("나이키", "스포츠 브랜드");
            Long productId = fixture.registerProduct(brandId, "운동화", new BigDecimal("50000"), 100, "편한 운동화");
            Long orderId = fixture.placeOrder(
                    List.of(new OrderRequest.PlaceItem(productId, 1)),
                    "otheruser", "Other1234!");

            Payment payment = fixture.createRequestedPayment(orderId, 2L, new BigDecimal("50000"));

            ResponseEntity<ApiResponse<PaymentV1Dto.PaymentResponse>> response = testRestTemplate.exchange(
                    PAYMENT_ENDPOINT + "/" + payment.getId() + "/verify", HttpMethod.POST,
                    new HttpEntity<>(fixture.userHeaders(LOGIN_ID, PASSWORD)),
                    new ParameterizedTypeReference<>() {}
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }

        @Test
        void 인증_헤더가_누락되면_401_응답() {
            ResponseEntity<ApiResponse<PaymentV1Dto.PaymentResponse>> response = testRestTemplate.exchange(
                    PAYMENT_ENDPOINT + "/1/verify", HttpMethod.POST,
                    new HttpEntity<>(null),
                    new ParameterizedTypeReference<>() {}
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }

        @Test
        void 인증에_실패하면_401_응답() {
            HttpHeaders headers = new HttpHeaders();
            headers.set("X-Loopers-LoginId", "wronguser");
            headers.set("X-Loopers-LoginPw", "wrongpw");

            ResponseEntity<ApiResponse<PaymentV1Dto.PaymentResponse>> response = testRestTemplate.exchange(
                    PAYMENT_ENDPOINT + "/1/verify", HttpMethod.POST,
                    new HttpEntity<>(null, headers),
                    new ParameterizedTypeReference<>() {}
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }
    }

    private ResponseEntity<ApiResponse<PaymentV1Dto.PaymentResponse>> postPayment(PaymentRequest.Request request) {
        return testRestTemplate.exchange(
                PAYMENT_ENDPOINT, HttpMethod.POST,
                new HttpEntity<>(request, fixture.userHeaders(LOGIN_ID, PASSWORD)),
                new ParameterizedTypeReference<>() {}
        );
    }
}
