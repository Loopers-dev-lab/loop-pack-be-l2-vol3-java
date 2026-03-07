package com.loopers.interfaces.api.order;

import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.PageResponse;
import com.loopers.interfaces.api.product.ProductAdminV1Dto;
import com.loopers.support.E2ETestFixture;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(E2ETestFixture.class)
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class OrderApiE2ETest {

    private static final String ENDPOINT = "/api/v1/orders";
    private static final String USER_LOGIN_ID = "testuser";
    private static final String USER_PASSWORD = "Test1234!";

    @Autowired
    private TestRestTemplate testRestTemplate;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @Autowired
    private E2ETestFixture fixture;

    @BeforeEach
    void setUp() {
        databaseCleanUp.truncateAllTables();
    }

    @Nested
    class 주문_요청 {

        @Test
        void 유효한_정보로_주문하면_200_응답과_생성된_주문_정보를_반환한다() {
            fixture.signUp("testuser", "Test1234!", "홍길동", "test@example.com");
            Long brandId = fixture.registerBrand("나이키", "스포츠 브랜드");
            Long productId1 = fixture.registerProduct(brandId, "운동화", new BigDecimal("50000"), 100, "편한 운동화");
            Long productId2 = fixture.registerProduct(brandId, "셔츠", new BigDecimal("30000"), 50, "멋진 셔츠");

            OrderRequest.Place request = new OrderRequest.Place(List.of(
                    new OrderRequest.PlaceItem(productId1, 2),
                    new OrderRequest.PlaceItem(productId2, 1)
            ));

            ResponseEntity<ApiResponse<OrderV1Dto.OrderResponse>> response = postOrder(request);

            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(response.getBody().data().id()).isNotNull(),
                    () -> assertThat(response.getBody().data().totalAmount()).isEqualByComparingTo(new BigDecimal("130000")),
                    () -> assertThat(response.getBody().data().discountAmount()).isEqualByComparingTo(BigDecimal.ZERO),
                    () -> assertThat(response.getBody().data().finalAmount()).isEqualByComparingTo(new BigDecimal("130000")),
                    () -> assertThat(response.getBody().data().couponId()).isNull(),
                    () -> assertThat(response.getBody().data().orderItems()).hasSize(2),
                    () -> assertThat(response.getBody().data().createdAt()).isNotNull()
            );
        }

        @Test
        void 주문_시_스냅샷_정보가_올바르게_저장된다() {
            fixture.signUp("testuser", "Test1234!", "홍길동", "test@example.com");
            Long brandId = fixture.registerBrand("나이키", "스포츠 브랜드");
            Long productId = fixture.registerProduct(brandId, "운동화", new BigDecimal("50000"), 100, "편한 운동화");

            OrderRequest.Place request = new OrderRequest.Place(List.of(
                    new OrderRequest.PlaceItem(productId, 3)
            ));

            ResponseEntity<ApiResponse<OrderV1Dto.OrderResponse>> response = postOrder(request);

            OrderV1Dto.OrderItemResponse item = response.getBody().data().orderItems().get(0);
            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(item.productId()).isEqualTo(productId),
                    () -> assertThat(item.productName()).isEqualTo("운동화"),
                    () -> assertThat(item.price()).isEqualByComparingTo(new BigDecimal("50000")),
                    () -> assertThat(item.quantity()).isEqualTo(3),
                    () -> assertThat(item.orderPrice()).isEqualByComparingTo(new BigDecimal("150000"))
            );
        }

        @Test
        void 주문_시_해당_상품의_재고가_주문_수량만큼_차감된다() {
            fixture.signUp("testuser", "Test1234!", "홍길동", "test@example.com");
            Long brandId = fixture.registerBrand("나이키", "스포츠 브랜드");
            Long productId = fixture.registerProduct(brandId, "운동화", new BigDecimal("50000"), 100, "편한 운동화");

            OrderRequest.Place request = new OrderRequest.Place(List.of(
                    new OrderRequest.PlaceItem(productId, 3)
            ));

            ResponseEntity<ApiResponse<OrderV1Dto.OrderResponse>> response = postOrder(request);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

            List<ProductAdminV1Dto.ProductResponse> products = getProductList();
            ProductAdminV1Dto.ProductResponse product = products.stream()
                    .filter(p -> p.id().equals(productId))
                    .findFirst()
                    .orElseThrow();

            assertThat(product.stockQuantity()).isEqualTo(97);
        }

        @Test
        void 미존재_상품이_포함되면_404_응답() {
            fixture.signUp("testuser", "Test1234!", "홍길동", "test@example.com");

            OrderRequest.Place request = new OrderRequest.Place(List.of(
                    new OrderRequest.PlaceItem(999L, 1)
            ));

            ResponseEntity<ApiResponse<Object>> response = testRestTemplate.exchange(
                    ENDPOINT, HttpMethod.POST,
                    new HttpEntity<>(request, userHeaders()),
                    new ParameterizedTypeReference<>() {}
            );

            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND),
                    () -> assertThat(response.getBody().meta().message()).contains("존재하지 않는 상품입니다")
            );
        }

        @Test
        void 재고가_부족하면_400_응답() {
            fixture.signUp("testuser", "Test1234!", "홍길동", "test@example.com");
            Long brandId = fixture.registerBrand("나이키", "스포츠 브랜드");
            Long productId = fixture.registerProduct(brandId, "운동화", new BigDecimal("50000"), 5, "편한 운동화");

            OrderRequest.Place request = new OrderRequest.Place(List.of(
                    new OrderRequest.PlaceItem(productId, 10)
            ));

            ResponseEntity<ApiResponse<Object>> response = testRestTemplate.exchange(
                    ENDPOINT, HttpMethod.POST,
                    new HttpEntity<>(request, userHeaders()),
                    new ParameterizedTypeReference<>() {}
            );

            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST),
                    () -> assertThat(response.getBody().meta().message()).contains("재고가 부족합니다")
            );
        }

        @Test
        void 재고_부족_시_전체_주문이_실패하며_어떤_상품의_재고도_차감되지_않는다() {
            fixture.signUp("testuser", "Test1234!", "홍길동", "test@example.com");
            Long brandId = fixture.registerBrand("나이키", "스포츠 브랜드");
            Long productId1 = fixture.registerProduct(brandId, "운동화", new BigDecimal("50000"), 100, "편한 운동화");
            Long productId2 = fixture.registerProduct(brandId, "셔츠", new BigDecimal("30000"), 3, "멋진 셔츠");

            OrderRequest.Place request = new OrderRequest.Place(List.of(
                    new OrderRequest.PlaceItem(productId1, 2),
                    new OrderRequest.PlaceItem(productId2, 10)
            ));

            ResponseEntity<ApiResponse<Object>> response = testRestTemplate.exchange(
                    ENDPOINT, HttpMethod.POST,
                    new HttpEntity<>(request, userHeaders()),
                    new ParameterizedTypeReference<>() {}
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

            List<ProductAdminV1Dto.ProductResponse> products = getProductList();
            ProductAdminV1Dto.ProductResponse product1 = products.stream()
                    .filter(p -> p.id().equals(productId1))
                    .findFirst()
                    .orElseThrow();
            ProductAdminV1Dto.ProductResponse product2 = products.stream()
                    .filter(p -> p.id().equals(productId2))
                    .findFirst()
                    .orElseThrow();

            assertAll(
                    () -> assertThat(product1.stockQuantity()).isEqualTo(100),
                    () -> assertThat(product2.stockQuantity()).isEqualTo(3)
            );
        }

        @Test
        void 동일_상품ID가_중복으로_포함되면_400_응답() {
            fixture.signUp("testuser", "Test1234!", "홍길동", "test@example.com");
            Long brandId = fixture.registerBrand("나이키", "스포츠 브랜드");
            Long productId = fixture.registerProduct(brandId, "운동화", new BigDecimal("50000"), 100, "편한 운동화");

            OrderRequest.Place request = new OrderRequest.Place(List.of(
                    new OrderRequest.PlaceItem(productId, 1),
                    new OrderRequest.PlaceItem(productId, 2)
            ));

            ResponseEntity<ApiResponse<Object>> response = testRestTemplate.exchange(
                    ENDPOINT, HttpMethod.POST,
                    new HttpEntity<>(request, userHeaders()),
                    new ParameterizedTypeReference<>() {}
            );

            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST),
                    () -> assertThat(response.getBody().meta().message()).contains("주문 상품이 중복되었습니다")
            );
        }

        @Test
        void 정액_쿠폰_적용_시_할인_금액만큼_차감된_최종_금액을_반환한다() {
            fixture.signUp("testuser", "Test1234!", "홍길동", "test@example.com");
            Long brandId = fixture.registerBrand("나이키", "스포츠 브랜드");
            Long productId = fixture.registerProduct(brandId, "운동화", new BigDecimal("50000"), 100, "편한 운동화");
            Long couponId = fixture.registerCoupon("5000원 할인", "FIXED", 5000,
                    BigDecimal.valueOf(10000), 100, LocalDateTime.now().plusDays(7));
            Long issuedCouponId = fixture.issueCoupon(couponId, "testuser", "Test1234!");

            OrderRequest.Place request = new OrderRequest.Place(List.of(
                    new OrderRequest.PlaceItem(productId, 2)
            ), issuedCouponId);

            ResponseEntity<ApiResponse<OrderV1Dto.OrderResponse>> response = postOrder(request);

            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(response.getBody().data().totalAmount()).isEqualByComparingTo(new BigDecimal("100000")),
                    () -> assertThat(response.getBody().data().discountAmount()).isEqualByComparingTo(new BigDecimal("5000")),
                    () -> assertThat(response.getBody().data().finalAmount()).isEqualByComparingTo(new BigDecimal("95000")),
                    () -> assertThat(response.getBody().data().couponId()).isEqualTo(issuedCouponId)
            );
        }

        @Test
        void 정률_쿠폰_적용_시_비율에_따른_할인_금액을_반환한다() {
            fixture.signUp("testuser", "Test1234!", "홍길동", "test@example.com");
            Long brandId = fixture.registerBrand("나이키", "스포츠 브랜드");
            Long productId = fixture.registerProduct(brandId, "운동화", new BigDecimal("50000"), 100, "편한 운동화");
            Long couponId = fixture.registerCoupon("10% 할인", "RATE", 10,
                    null, 100, LocalDateTime.now().plusDays(7));
            Long issuedCouponId = fixture.issueCoupon(couponId, "testuser", "Test1234!");

            OrderRequest.Place request = new OrderRequest.Place(List.of(
                    new OrderRequest.PlaceItem(productId, 2)
            ), issuedCouponId);

            ResponseEntity<ApiResponse<OrderV1Dto.OrderResponse>> response = postOrder(request);

            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(response.getBody().data().totalAmount()).isEqualByComparingTo(new BigDecimal("100000")),
                    () -> assertThat(response.getBody().data().discountAmount()).isEqualByComparingTo(new BigDecimal("10000")),
                    () -> assertThat(response.getBody().data().finalAmount()).isEqualByComparingTo(new BigDecimal("90000")),
                    () -> assertThat(response.getBody().data().couponId()).isEqualTo(issuedCouponId)
            );
        }

        @Test
        void 쿠폰_적용_시_해당_발급_쿠폰이_USED_상태로_변경된다() {
            fixture.signUp("testuser", "Test1234!", "홍길동", "test@example.com");
            Long brandId = fixture.registerBrand("나이키", "스포츠 브랜드");
            Long productId = fixture.registerProduct(brandId, "운동화", new BigDecimal("50000"), 100, "편한 운동화");
            Long couponId = fixture.registerCoupon("5000원 할인", "FIXED", 5000,
                    null, 100, LocalDateTime.now().plusDays(7));
            Long issuedCouponId = fixture.issueCoupon(couponId, "testuser", "Test1234!");

            postOrder(new OrderRequest.Place(List.of(
                    new OrderRequest.PlaceItem(productId, 1)
            ), issuedCouponId));

            // 같은 쿠폰으로 재주문 시 사용 불가
            Long productId2 = fixture.registerProduct(brandId, "셔츠", new BigDecimal("30000"), 100, "멋진 셔츠");
            ResponseEntity<ApiResponse<Object>> response = testRestTemplate.exchange(
                    ENDPOINT, HttpMethod.POST,
                    new HttpEntity<>(new OrderRequest.Place(List.of(
                            new OrderRequest.PlaceItem(productId2, 1)
                    ), issuedCouponId), userHeaders()),
                    new ParameterizedTypeReference<>() {}
            );

            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST),
                    () -> assertThat(response.getBody().meta().message()).contains("사용할 수 없는 쿠폰입니다")
            );
        }

        @Test
        void 미존재_발급_쿠폰이면_404_응답() {
            fixture.signUp("testuser", "Test1234!", "홍길동", "test@example.com");
            Long brandId = fixture.registerBrand("나이키", "스포츠 브랜드");
            Long productId = fixture.registerProduct(brandId, "운동화", new BigDecimal("50000"), 100, "편한 운동화");

            OrderRequest.Place request = new OrderRequest.Place(List.of(
                    new OrderRequest.PlaceItem(productId, 1)
            ), 999L);

            ResponseEntity<ApiResponse<Object>> response = testRestTemplate.exchange(
                    ENDPOINT, HttpMethod.POST,
                    new HttpEntity<>(request, userHeaders()),
                    new ParameterizedTypeReference<>() {}
            );

            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND),
                    () -> assertThat(response.getBody().meta().message()).contains("존재하지 않는 쿠폰입니다")
            );
        }

        @Test
        void 타인_소유의_쿠폰이면_404_응답() {
            fixture.signUp("testuser", "Test1234!", "홍길동", "test@example.com");
            fixture.signUp("otheruser", "Other1234!", "김철수", "other@example.com");
            Long brandId = fixture.registerBrand("나이키", "스포츠 브랜드");
            Long productId = fixture.registerProduct(brandId, "운동화", new BigDecimal("50000"), 100, "편한 운동화");
            Long couponId = fixture.registerCoupon("5000원 할인", "FIXED", 5000,
                    null, 100, LocalDateTime.now().plusDays(7));
            Long otherIssuedCouponId = fixture.issueCoupon(couponId, "otheruser", "Other1234!");

            OrderRequest.Place request = new OrderRequest.Place(List.of(
                    new OrderRequest.PlaceItem(productId, 1)
            ), otherIssuedCouponId);

            ResponseEntity<ApiResponse<Object>> response = testRestTemplate.exchange(
                    ENDPOINT, HttpMethod.POST,
                    new HttpEntity<>(request, userHeaders()),
                    new ParameterizedTypeReference<>() {}
            );

            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND),
                    () -> assertThat(response.getBody().meta().message()).contains("존재하지 않는 쿠폰입니다")
            );
        }

        @Test
        void 삭제된_상품이_포함되면_404_응답() {
            fixture.signUp("testuser", "Test1234!", "홍길동", "test@example.com");
            Long brandId = fixture.registerBrand("나이키", "스포츠 브랜드");
            Long productId = fixture.registerProduct(brandId, "운동화", new BigDecimal("50000"), 100, "편한 운동화");
            fixture.deleteProduct(productId);

            OrderRequest.Place request = new OrderRequest.Place(List.of(
                    new OrderRequest.PlaceItem(productId, 1)
            ));

            ResponseEntity<ApiResponse<Object>> response = testRestTemplate.exchange(
                    ENDPOINT, HttpMethod.POST,
                    new HttpEntity<>(request, userHeaders()),
                    new ParameterizedTypeReference<>() {}
            );

            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND),
                    () -> assertThat(response.getBody().meta().message()).contains("존재하지 않는 상품입니다")
            );
        }

        @Test
        void 동시에_같은_쿠폰으로_주문해도_쿠폰은_한_번만_사용된다() throws InterruptedException {
            fixture.signUp("testuser", "Test1234!", "홍길동", "test@example.com");
            Long brandId = fixture.registerBrand("나이키", "스포츠 브랜드");
            Long productId1 = fixture.registerProduct(brandId, "운동화", new BigDecimal("50000"), 100, "편한 운동화");
            Long productId2 = fixture.registerProduct(brandId, "셔츠", new BigDecimal("30000"), 100, "멋진 셔츠");
            Long couponId = fixture.registerCoupon("5000원 할인", "FIXED", 5000,
                    null, 100, LocalDateTime.now().plusDays(7));
            Long issuedCouponId = fixture.issueCoupon(couponId, "testuser", "Test1234!");

            int threadCount = 2;
            ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
            CountDownLatch latch = new CountDownLatch(threadCount);
            List<HttpStatus> statuses = Collections.synchronizedList(new ArrayList<HttpStatus>());
            Long[] productIds = {productId1, productId2};

            for (int i = 0; i < threadCount; i++) {
                Long productId = productIds[i];
                executorService.submit(() -> {
                    try {
                        ResponseEntity<ApiResponse<Object>> res = testRestTemplate.exchange(
                                ENDPOINT, HttpMethod.POST,
                                new HttpEntity<>(new OrderRequest.Place(List.of(
                                        new OrderRequest.PlaceItem(productId, 1)
                                ), issuedCouponId), userHeaders()),
                                new ParameterizedTypeReference<>() {}
                        );
                        statuses.add(HttpStatus.valueOf(res.getStatusCode().value()));
                    } finally {
                        latch.countDown();
                    }
                });
            }

            latch.await();
            executorService.shutdown();

            assertAll(
                    () -> assertThat(statuses).hasSize(2),
                    () -> assertThat(statuses).containsExactlyInAnyOrder(HttpStatus.OK, HttpStatus.BAD_REQUEST)
            );
        }

        @Test
        void 최소_주문_금액_미달이면_400_응답() {
            fixture.signUp("testuser", "Test1234!", "홍길동", "test@example.com");
            Long brandId = fixture.registerBrand("나이키", "스포츠 브랜드");
            Long productId = fixture.registerProduct(brandId, "운동화", new BigDecimal("5000"), 100, "편한 운동화");
            Long couponId = fixture.registerCoupon("5000원 할인", "FIXED", 5000,
                    BigDecimal.valueOf(50000), 100, LocalDateTime.now().plusDays(7));
            Long issuedCouponId = fixture.issueCoupon(couponId, "testuser", "Test1234!");

            OrderRequest.Place request = new OrderRequest.Place(List.of(
                    new OrderRequest.PlaceItem(productId, 1)
            ), issuedCouponId);

            ResponseEntity<ApiResponse<Object>> response = testRestTemplate.exchange(
                    ENDPOINT, HttpMethod.POST,
                    new HttpEntity<>(request, userHeaders()),
                    new ParameterizedTypeReference<>() {}
            );

            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST),
                    () -> assertThat(response.getBody().meta().message()).contains("최소 주문 금액 조건을 충족하지 않습니다")
            );
        }

        @Test
        void 요청_필드_규칙_위반_시_400_응답() {
            fixture.signUp("testuser", "Test1234!", "홍길동", "test@example.com");

            OrderRequest.Place request = new OrderRequest.Place(List.of(
                    new OrderRequest.PlaceItem(1L, 0)
            ));

            ResponseEntity<ApiResponse<Object>> response = testRestTemplate.exchange(
                    ENDPOINT, HttpMethod.POST,
                    new HttpEntity<>(request, userHeaders()),
                    new ParameterizedTypeReference<>() {}
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @Test
        void 인증_헤더가_누락되면_401_응답() {
            OrderRequest.Place request = new OrderRequest.Place(List.of(
                    new OrderRequest.PlaceItem(1L, 1)
            ));

            ResponseEntity<ApiResponse<Object>> response = testRestTemplate.exchange(
                    ENDPOINT, HttpMethod.POST,
                    new HttpEntity<>(request, new HttpHeaders()),
                    new ParameterizedTypeReference<>() {}
            );

            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED),
                    () -> assertThat(response.getBody().meta().message()).contains("인증 헤더가 필요합니다")
            );
        }

        @Test
        void 인증에_실패하면_401_응답() {
            OrderRequest.Place request = new OrderRequest.Place(List.of(
                    new OrderRequest.PlaceItem(1L, 1)
            ));

            HttpHeaders headers = new HttpHeaders();
            headers.set("X-Loopers-LoginId", "notexist");
            headers.set("X-Loopers-LoginPw", "WrongPass1!");

            ResponseEntity<ApiResponse<Object>> response = testRestTemplate.exchange(
                    ENDPOINT, HttpMethod.POST,
                    new HttpEntity<>(request, headers),
                    new ParameterizedTypeReference<>() {}
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }
    }

    @Nested
    class 주문_목록_조회 {

        @Test
        void 조건_없이_조회하면_본인의_주문만_최신순으로_페이징하여_200_응답한다() {
            fixture.signUp("testuser", "Test1234!", "홍길동", "test@example.com");
            Long brandId = fixture.registerBrand("나이키", "스포츠 브랜드");
            Long productId1 = fixture.registerProduct(brandId, "운동화", new BigDecimal("50000"), 100, "편한 운동화");
            Long productId2 = fixture.registerProduct(brandId, "셔츠", new BigDecimal("30000"), 100, "멋진 셔츠");

            postOrder(new OrderRequest.Place(List.of(new OrderRequest.PlaceItem(productId1, 1))));
            postOrder(new OrderRequest.Place(List.of(new OrderRequest.PlaceItem(productId2, 2))));

            ResponseEntity<ApiResponse<PageResponse<OrderV1Dto.OrderListResponse>>> response =
                    getOrderList("");

            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(response.getBody().data().content()).hasSize(2),
                    () -> assertThat(response.getBody().data().totalElements()).isEqualTo(2),
                    () -> assertThat(response.getBody().data().content().get(0).createdAt())
                            .isAfterOrEqualTo(response.getBody().data().content().get(1).createdAt())
            );
        }

        @Test
        void 타인의_주문은_반환하지_않는다() {
            fixture.signUp("testuser", "Test1234!", "홍길동", "test@example.com");
            fixture.signUp("otheruser", "Other1234!", "김철수", "other@example.com");
            Long brandId = fixture.registerBrand("나이키", "스포츠 브랜드");
            Long productId = fixture.registerProduct(brandId, "운동화", new BigDecimal("50000"), 100, "편한 운동화");

            postOrder(new OrderRequest.Place(List.of(new OrderRequest.PlaceItem(productId, 1))));
            postOrderAs("otheruser", "Other1234!",
                    new OrderRequest.Place(List.of(new OrderRequest.PlaceItem(productId, 1))));

            ResponseEntity<ApiResponse<PageResponse<OrderV1Dto.OrderListResponse>>> response =
                    getOrderList("");

            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(response.getBody().data().content()).hasSize(1)
            );
        }

        @Test
        void 시작일만_지정하면_해당일_이후_주문만_반환한다() {
            fixture.signUp("testuser", "Test1234!", "홍길동", "test@example.com");
            Long brandId = fixture.registerBrand("나이키", "스포츠 브랜드");
            Long productId = fixture.registerProduct(brandId, "운동화", new BigDecimal("50000"), 100, "편한 운동화");
            postOrder(new OrderRequest.Place(List.of(new OrderRequest.PlaceItem(productId, 1))));

            LocalDate today = LocalDate.now();
            LocalDate tomorrow = today.plusDays(1);

            ResponseEntity<ApiResponse<PageResponse<OrderV1Dto.OrderListResponse>>> responseToday =
                    getOrderList("?startDate=" + today);
            ResponseEntity<ApiResponse<PageResponse<OrderV1Dto.OrderListResponse>>> responseTomorrow =
                    getOrderList("?startDate=" + tomorrow);

            assertAll(
                    () -> assertThat(responseToday.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(responseToday.getBody().data().content()).hasSize(1),
                    () -> assertThat(responseTomorrow.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(responseTomorrow.getBody().data().content()).isEmpty()
            );
        }

        @Test
        void 종료일만_지정하면_해당일_이전_주문만_반환한다() {
            fixture.signUp("testuser", "Test1234!", "홍길동", "test@example.com");
            Long brandId = fixture.registerBrand("나이키", "스포츠 브랜드");
            Long productId = fixture.registerProduct(brandId, "운동화", new BigDecimal("50000"), 100, "편한 운동화");
            postOrder(new OrderRequest.Place(List.of(new OrderRequest.PlaceItem(productId, 1))));

            LocalDate today = LocalDate.now();
            LocalDate yesterday = today.minusDays(1);

            ResponseEntity<ApiResponse<PageResponse<OrderV1Dto.OrderListResponse>>> responseToday =
                    getOrderList("?endDate=" + today);
            ResponseEntity<ApiResponse<PageResponse<OrderV1Dto.OrderListResponse>>> responseYesterday =
                    getOrderList("?endDate=" + yesterday);

            assertAll(
                    () -> assertThat(responseToday.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(responseToday.getBody().data().content()).hasSize(1),
                    () -> assertThat(responseYesterday.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(responseYesterday.getBody().data().content()).isEmpty()
            );
        }

        @Test
        void 시작일과_종료일을_모두_지정하면_해당_기간_내_주문만_반환한다() {
            fixture.signUp("testuser", "Test1234!", "홍길동", "test@example.com");
            Long brandId = fixture.registerBrand("나이키", "스포츠 브랜드");
            Long productId = fixture.registerProduct(brandId, "운동화", new BigDecimal("50000"), 100, "편한 운동화");
            postOrder(new OrderRequest.Place(List.of(new OrderRequest.PlaceItem(productId, 1))));

            LocalDate today = LocalDate.now();

            ResponseEntity<ApiResponse<PageResponse<OrderV1Dto.OrderListResponse>>> responseInRange =
                    getOrderList("?startDate=" + today + "&endDate=" + today);
            ResponseEntity<ApiResponse<PageResponse<OrderV1Dto.OrderListResponse>>> responseOutOfRange =
                    getOrderList("?startDate=" + today.plusDays(1) + "&endDate=" + today.plusDays(2));

            assertAll(
                    () -> assertThat(responseInRange.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(responseInRange.getBody().data().content()).hasSize(1),
                    () -> assertThat(responseOutOfRange.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(responseOutOfRange.getBody().data().content()).isEmpty()
            );
        }

        @Test
        void 시작일이_종료일보다_미래이면_400_응답() {
            fixture.signUp("testuser", "Test1234!", "홍길동", "test@example.com");

            LocalDate today = LocalDate.now();

            ResponseEntity<ApiResponse<Object>> response = testRestTemplate.exchange(
                    ENDPOINT + "?startDate=" + today.plusDays(1) + "&endDate=" + today,
                    HttpMethod.GET,
                    new HttpEntity<>(userHeaders()),
                    new ParameterizedTypeReference<>() {}
            );

            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST),
                    () -> assertThat(response.getBody().meta().message()).contains("시작일은 종료일 이전이어야 합니다")
            );
        }

        @Test
        void 결과가_없으면_빈_목록을_반환한다() {
            fixture.signUp("testuser", "Test1234!", "홍길동", "test@example.com");

            ResponseEntity<ApiResponse<PageResponse<OrderV1Dto.OrderListResponse>>> response =
                    getOrderList("");

            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(response.getBody().data().content()).isEmpty(),
                    () -> assertThat(response.getBody().data().totalElements()).isZero()
            );
        }

        @Test
        void 요청_필드_규칙_위반_시_400_응답() {
            fixture.signUp("testuser", "Test1234!", "홍길동", "test@example.com");

            ResponseEntity<ApiResponse<Object>> response = testRestTemplate.exchange(
                    ENDPOINT + "?page=-1",
                    HttpMethod.GET,
                    new HttpEntity<>(userHeaders()),
                    new ParameterizedTypeReference<>() {}
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @Test
        void 인증_헤더가_누락되면_401_응답() {
            ResponseEntity<ApiResponse<Object>> response = testRestTemplate.exchange(
                    ENDPOINT,
                    HttpMethod.GET,
                    new HttpEntity<>(new HttpHeaders()),
                    new ParameterizedTypeReference<>() {}
            );

            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED),
                    () -> assertThat(response.getBody().meta().message()).contains("인증 헤더가 필요합니다")
            );
        }

        @Test
        void 인증에_실패하면_401_응답() {
            HttpHeaders headers = new HttpHeaders();
            headers.set("X-Loopers-LoginId", "notexist");
            headers.set("X-Loopers-LoginPw", "WrongPass1!");

            ResponseEntity<ApiResponse<Object>> response = testRestTemplate.exchange(
                    ENDPOINT,
                    HttpMethod.GET,
                    new HttpEntity<>(headers),
                    new ParameterizedTypeReference<>() {}
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }
    }

    @Nested
    class 주문_상세_조회 {

        @Test
        void 본인의_주문을_조회하면_200_응답과_주문_상세_정보를_반환한다() {
            fixture.signUp("testuser", "Test1234!", "홍길동", "test@example.com");
            Long brandId = fixture.registerBrand("나이키", "스포츠 브랜드");
            Long productId1 = fixture.registerProduct(brandId, "운동화", new BigDecimal("50000"), 100, "편한 운동화");
            Long productId2 = fixture.registerProduct(brandId, "셔츠", new BigDecimal("30000"), 100, "멋진 셔츠");

            ResponseEntity<ApiResponse<OrderV1Dto.OrderResponse>> createResponse = postOrder(
                    new OrderRequest.Place(List.of(
                            new OrderRequest.PlaceItem(productId1, 2),
                            new OrderRequest.PlaceItem(productId2, 1)
                    ))
            );
            Long orderId = createResponse.getBody().data().id();

            ResponseEntity<ApiResponse<OrderV1Dto.OrderResponse>> response = getOrderDetail(orderId);

            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(response.getBody().data().id()).isEqualTo(orderId),
                    () -> assertThat(response.getBody().data().totalAmount()).isEqualByComparingTo(new BigDecimal("130000")),
                    () -> assertThat(response.getBody().data().discountAmount()).isEqualByComparingTo(BigDecimal.ZERO),
                    () -> assertThat(response.getBody().data().finalAmount()).isEqualByComparingTo(new BigDecimal("130000")),
                    () -> assertThat(response.getBody().data().couponId()).isNull(),
                    () -> assertThat(response.getBody().data().orderItems()).hasSize(2),
                    () -> assertThat(response.getBody().data().createdAt()).isNotNull()
            );
        }

        @Test
        void 주문_상품은_스냅샷_정보로_반환한다() {
            fixture.signUp("testuser", "Test1234!", "홍길동", "test@example.com");
            Long brandId = fixture.registerBrand("나이키", "스포츠 브랜드");
            Long productId = fixture.registerProduct(brandId, "운동화", new BigDecimal("50000"), 100, "편한 운동화");

            ResponseEntity<ApiResponse<OrderV1Dto.OrderResponse>> createResponse = postOrder(
                    new OrderRequest.Place(List.of(new OrderRequest.PlaceItem(productId, 3)))
            );
            Long orderId = createResponse.getBody().data().id();

            ResponseEntity<ApiResponse<OrderV1Dto.OrderResponse>> response = getOrderDetail(orderId);

            OrderV1Dto.OrderItemResponse item = response.getBody().data().orderItems().get(0);
            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(item.productId()).isEqualTo(productId),
                    () -> assertThat(item.productName()).isEqualTo("운동화"),
                    () -> assertThat(item.price()).isEqualByComparingTo(new BigDecimal("50000")),
                    () -> assertThat(item.quantity()).isEqualTo(3),
                    () -> assertThat(item.orderPrice()).isEqualByComparingTo(new BigDecimal("150000"))
            );
        }

        @Test
        void 존재하지_않는_주문이면_404_응답() {
            fixture.signUp("testuser", "Test1234!", "홍길동", "test@example.com");

            ResponseEntity<ApiResponse<Object>> response = testRestTemplate.exchange(
                    ENDPOINT + "/999",
                    HttpMethod.GET,
                    new HttpEntity<>(userHeaders()),
                    new ParameterizedTypeReference<>() {}
            );

            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND),
                    () -> assertThat(response.getBody().meta().message()).contains("존재하지 않는 주문입니다")
            );
        }

        @Test
        void 본인의_주문이_아니면_404_응답() {
            fixture.signUp("testuser", "Test1234!", "홍길동", "test@example.com");
            fixture.signUp("otheruser", "Other1234!", "김철수", "other@example.com");
            Long brandId = fixture.registerBrand("나이키", "스포츠 브랜드");
            Long productId = fixture.registerProduct(brandId, "운동화", new BigDecimal("50000"), 100, "편한 운동화");

            ResponseEntity<ApiResponse<OrderV1Dto.OrderResponse>> createResponse = postOrderAs(
                    "otheruser", "Other1234!",
                    new OrderRequest.Place(List.of(new OrderRequest.PlaceItem(productId, 1)))
            );
            Long otherOrderId = createResponse.getBody().data().id();

            ResponseEntity<ApiResponse<Object>> response = testRestTemplate.exchange(
                    ENDPOINT + "/" + otherOrderId,
                    HttpMethod.GET,
                    new HttpEntity<>(userHeaders()),
                    new ParameterizedTypeReference<>() {}
            );

            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND),
                    () -> assertThat(response.getBody().meta().message()).contains("존재하지 않는 주문입니다")
            );
        }

        @Test
        void 인증_헤더가_누락되면_401_응답() {
            ResponseEntity<ApiResponse<Object>> response = testRestTemplate.exchange(
                    ENDPOINT + "/1",
                    HttpMethod.GET,
                    new HttpEntity<>(new HttpHeaders()),
                    new ParameterizedTypeReference<>() {}
            );

            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED),
                    () -> assertThat(response.getBody().meta().message()).contains("인증 헤더가 필요합니다")
            );
        }

        @Test
        void 인증에_실패하면_401_응답() {
            HttpHeaders headers = new HttpHeaders();
            headers.set("X-Loopers-LoginId", "notexist");
            headers.set("X-Loopers-LoginPw", "WrongPass1!");

            ResponseEntity<ApiResponse<Object>> response = testRestTemplate.exchange(
                    ENDPOINT + "/1",
                    HttpMethod.GET,
                    new HttpEntity<>(headers),
                    new ParameterizedTypeReference<>() {}
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }
    }

    // --- 헬퍼 메서드 ---

    private List<ProductAdminV1Dto.ProductResponse> getProductList() {
        ResponseEntity<ApiResponse<PageResponse<ProductAdminV1Dto.ProductResponse>>> response = testRestTemplate.exchange(
                "/api-admin/v1/products", HttpMethod.GET,
                new HttpEntity<>(fixture.adminHeaders()),
                new ParameterizedTypeReference<>() {}
        );
        return response.getBody().data().content();
    }

    private ResponseEntity<ApiResponse<OrderV1Dto.OrderResponse>> postOrder(OrderRequest.Place request) {
        return testRestTemplate.exchange(
                ENDPOINT, HttpMethod.POST,
                new HttpEntity<>(request, userHeaders()),
                new ParameterizedTypeReference<>() {}
        );
    }

    private ResponseEntity<ApiResponse<OrderV1Dto.OrderResponse>> postOrderAs(
            String loginId, String password, OrderRequest.Place request) {
        return testRestTemplate.exchange(
                ENDPOINT, HttpMethod.POST,
                new HttpEntity<>(request, fixture.userHeaders(loginId, password)),
                new ParameterizedTypeReference<>() {}
        );
    }

    private ResponseEntity<ApiResponse<OrderV1Dto.OrderResponse>> getOrderDetail(Long orderId) {
        return testRestTemplate.exchange(
                ENDPOINT + "/" + orderId,
                HttpMethod.GET,
                new HttpEntity<>(userHeaders()),
                new ParameterizedTypeReference<>() {}
        );
    }

    private ResponseEntity<ApiResponse<PageResponse<OrderV1Dto.OrderListResponse>>> getOrderList(String queryString) {
        return testRestTemplate.exchange(
                ENDPOINT + queryString,
                HttpMethod.GET,
                new HttpEntity<>(userHeaders()),
                new ParameterizedTypeReference<>() {}
        );
    }

    private HttpHeaders userHeaders() {
        return fixture.userHeaders(USER_LOGIN_ID, USER_PASSWORD);
    }
}
