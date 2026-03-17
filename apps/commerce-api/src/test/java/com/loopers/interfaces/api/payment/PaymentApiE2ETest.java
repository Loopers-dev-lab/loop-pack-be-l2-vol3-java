package com.loopers.interfaces.api.payment;

import com.loopers.domain.payment.CardType;
import com.loopers.domain.payment.PaymentStatus;
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
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

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
    private DatabaseCleanUp databaseCleanUp;

    @BeforeEach
    void setUp() {
        databaseCleanUp.truncateAllTables();
    }

    @Nested
    class 결제_요청 {

        @Test
        void 주문이_존재하지_않으면_404_응답() {
            fixture.signUp(LOGIN_ID, PASSWORD, "홍길동", "test@example.com");

            PaymentRequest.Request request = new PaymentRequest.Request(
                    999L, CardType.SAMSUNG, "1234-5678-9012-3456");

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
                    orderId, CardType.SAMSUNG, "1234-5678-9012-3456");

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

            // 결제 하나 직접 생성 (PENDING 상태)
            paymentService.createPayment(orderId, 1L, CardType.SAMSUNG, "1234-5678-9012-3456", new BigDecimal("50000"));

            PaymentRequest.Request request = new PaymentRequest.Request(
                    orderId, CardType.SAMSUNG, "9999-8888-7777-6666");

            ResponseEntity<ApiResponse<PaymentV1Dto.PaymentResponse>> response = postPayment(request);

            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT),
                    () -> assertThat(response.getBody().meta().message()).contains("이미 결제가 진행 중이거나 완료된 주문입니다")
            );
        }

        @Test
        void 요청_필드_규칙_위반_시_400_응답() {
            fixture.signUp(LOGIN_ID, PASSWORD, "홍길동", "test@example.com");

            PaymentRequest.Request request = new PaymentRequest.Request(
                    null, null, "invalid-card");

            ResponseEntity<ApiResponse<PaymentV1Dto.PaymentResponse>> response = postPayment(request);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @Test
        void 인증_헤더가_누락되면_401_응답() {
            PaymentRequest.Request request = new PaymentRequest.Request(
                    1L, CardType.SAMSUNG, "1234-5678-9012-3456");

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
                    1L, CardType.SAMSUNG, "1234-5678-9012-3456");

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

            // userId=1L로 가정 (첫 번째 회원가입)
            var payment = paymentService.createPayment(orderId, 1L, CardType.SAMSUNG, "1234-5678-9012-3456", new BigDecimal("50000"));

            ResponseEntity<ApiResponse<PaymentV1Dto.PaymentResponse>> response = testRestTemplate.exchange(
                    PAYMENT_ENDPOINT + "/" + payment.getId(), HttpMethod.GET,
                    new HttpEntity<>(fixture.userHeaders(LOGIN_ID, PASSWORD)),
                    new ParameterizedTypeReference<>() {}
            );

            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(response.getBody().data().id()).isEqualTo(payment.getId()),
                    () -> assertThat(response.getBody().data().status()).isEqualTo(PaymentStatus.PENDING),
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
    }

    private ResponseEntity<ApiResponse<PaymentV1Dto.PaymentResponse>> postPayment(PaymentRequest.Request request) {
        return testRestTemplate.exchange(
                PAYMENT_ENDPOINT, HttpMethod.POST,
                new HttpEntity<>(request, fixture.userHeaders(LOGIN_ID, PASSWORD)),
                new ParameterizedTypeReference<>() {}
        );
    }
}
