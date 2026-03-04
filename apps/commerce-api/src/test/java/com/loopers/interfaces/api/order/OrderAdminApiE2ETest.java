package com.loopers.interfaces.api.order;

import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.PageResponse;
import com.loopers.support.E2ETestFixture;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(E2ETestFixture.class)
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class OrderAdminApiE2ETest {

    private static final String ENDPOINT = "/api-admin/v1/orders";
    private static final String USER_LOGIN_ID = "testuser";
    private static final String USER_PASSWORD = "Test1234!";

    @Autowired
    private TestRestTemplate testRestTemplate;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @Autowired
    private E2ETestFixture fixture;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @Nested
    class 주문_목록_조회_관리자 {

        @Test
        void 전체_주문을_최신순으로_페이징하여_200_응답한다() {
            fixture.signUp("testuser", "Test1234!", "홍길동", "test@example.com");
            Long brandId = fixture.registerBrand("나이키", "스포츠 브랜드");
            Long productId1 = fixture.registerProduct(brandId, "운동화", new BigDecimal("50000"), 100, "편한 운동화");
            Long productId2 = fixture.registerProduct(brandId, "셔츠", new BigDecimal("30000"), 100, "멋진 셔츠");

            placeOrder(new OrderRequest.Place(List.of(new OrderRequest.PlaceItem(productId1, 1))));
            placeOrder(new OrderRequest.Place(List.of(new OrderRequest.PlaceItem(productId2, 2))));

            ResponseEntity<ApiResponse<PageResponse<OrderAdminV1Dto.OrderListResponse>>> response =
                    getList("");

            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(response.getBody().data().content()).hasSize(2),
                    () -> assertThat(response.getBody().data().totalElements()).isEqualTo(2),
                    () -> assertThat(response.getBody().data().page()).isEqualTo(0),
                    () -> assertThat(response.getBody().data().size()).isEqualTo(20),
                    () -> assertThat(response.getBody().data().content().get(0).createdAt())
                            .isAfterOrEqualTo(response.getBody().data().content().get(1).createdAt())
            );
        }

        @Test
        void 모든_사용자의_주문이_포함된다() {
            fixture.signUp("testuser", "Test1234!", "홍길동", "test@example.com");
            fixture.signUp("otheruser", "Other1234!", "김철수", "other@example.com");
            Long brandId = fixture.registerBrand("나이키", "스포츠 브랜드");
            Long productId = fixture.registerProduct(brandId, "운동화", new BigDecimal("50000"), 100, "편한 운동화");

            placeOrder(new OrderRequest.Place(List.of(new OrderRequest.PlaceItem(productId, 1))));
            placeOrderAs("otheruser", "Other1234!",
                    new OrderRequest.Place(List.of(new OrderRequest.PlaceItem(productId, 1))));

            ResponseEntity<ApiResponse<PageResponse<OrderAdminV1Dto.OrderListResponse>>> response =
                    getList("");

            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(response.getBody().data().content()).hasSize(2),
                    () -> assertThat(response.getBody().data().content())
                            .extracting(OrderAdminV1Dto.OrderListResponse::userId)
                            .doesNotHaveDuplicates()
            );
        }

        @Test
        void 결과가_없으면_빈_목록을_반환한다() {
            ResponseEntity<ApiResponse<PageResponse<OrderAdminV1Dto.OrderListResponse>>> response =
                    getList("");

            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(response.getBody().data().content()).isEmpty(),
                    () -> assertThat(response.getBody().data().totalElements()).isZero()
            );
        }

        @Test
        void 요청_필드_규칙_위반_시_400_응답() {
            ResponseEntity<ApiResponse<Object>> response = testRestTemplate.exchange(
                    ENDPOINT + "?page=-1", HttpMethod.GET,
                    new HttpEntity<>(fixture.adminHeaders()),
                    new ParameterizedTypeReference<>() {}
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @Test
        void 인증_헤더가_누락되면_401_응답() {
            ResponseEntity<ApiResponse<Object>> response = testRestTemplate.exchange(
                    ENDPOINT, HttpMethod.GET,
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
            headers.set("X-Loopers-Ldap", "wrong-ldap");

            ResponseEntity<ApiResponse<Object>> response = testRestTemplate.exchange(
                    ENDPOINT, HttpMethod.GET,
                    new HttpEntity<>(headers),
                    new ParameterizedTypeReference<>() {}
            );

            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED),
                    () -> assertThat(response.getBody().meta().message()).contains("인증에 실패했습니다")
            );
        }
    }

    @Nested
    class 주문_상세_조회_관리자 {

        @Test
        void 주문을_조회하면_200_응답과_주문_상세_정보를_반환한다() {
            fixture.signUp("testuser", "Test1234!", "홍길동", "test@example.com");
            Long brandId = fixture.registerBrand("나이키", "스포츠 브랜드");
            Long productId1 = fixture.registerProduct(brandId, "운동화", new BigDecimal("50000"), 100, "편한 운동화");
            Long productId2 = fixture.registerProduct(brandId, "셔츠", new BigDecimal("30000"), 100, "멋진 셔츠");

            Long orderId = placeOrder(new OrderRequest.Place(List.of(
                    new OrderRequest.PlaceItem(productId1, 2),
                    new OrderRequest.PlaceItem(productId2, 1)
            )));

            ResponseEntity<ApiResponse<OrderAdminV1Dto.OrderResponse>> response = getDetail(orderId);

            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(response.getBody().data().id()).isEqualTo(orderId),
                    () -> assertThat(response.getBody().data().userId()).isNotNull(),
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

            Long orderId = placeOrder(new OrderRequest.Place(List.of(
                    new OrderRequest.PlaceItem(productId, 3)
            )));

            ResponseEntity<ApiResponse<OrderAdminV1Dto.OrderResponse>> response = getDetail(orderId);

            OrderAdminV1Dto.OrderItemResponse item = response.getBody().data().orderItems().get(0);
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
            ResponseEntity<ApiResponse<Object>> response = testRestTemplate.exchange(
                    ENDPOINT + "/999", HttpMethod.GET,
                    new HttpEntity<>(fixture.adminHeaders()),
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
                    ENDPOINT + "/1", HttpMethod.GET,
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
            headers.set("X-Loopers-Ldap", "wrong-ldap");

            ResponseEntity<ApiResponse<Object>> response = testRestTemplate.exchange(
                    ENDPOINT + "/1", HttpMethod.GET,
                    new HttpEntity<>(headers),
                    new ParameterizedTypeReference<>() {}
            );

            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED),
                    () -> assertThat(response.getBody().meta().message()).contains("인증에 실패했습니다")
            );
        }
    }

    // --- 헬퍼 메서드 ---

    private Long placeOrder(OrderRequest.Place request) {
        ResponseEntity<ApiResponse<OrderV1Dto.OrderResponse>> response = testRestTemplate.exchange(
                "/api/v1/orders", HttpMethod.POST,
                new HttpEntity<>(request, userHeaders()),
                new ParameterizedTypeReference<>() {}
        );
        return response.getBody().data().id();
    }

    private void placeOrderAs(String loginId, String password, OrderRequest.Place request) {
        testRestTemplate.exchange(
                "/api/v1/orders", HttpMethod.POST,
                new HttpEntity<>(request, fixture.userHeaders(loginId, password)),
                new ParameterizedTypeReference<ApiResponse<OrderV1Dto.OrderResponse>>() {}
        );
    }

    private ResponseEntity<ApiResponse<OrderAdminV1Dto.OrderResponse>> getDetail(Long orderId) {
        return testRestTemplate.exchange(
                ENDPOINT + "/" + orderId, HttpMethod.GET,
                new HttpEntity<>(fixture.adminHeaders()),
                new ParameterizedTypeReference<>() {}
        );
    }

    private ResponseEntity<ApiResponse<PageResponse<OrderAdminV1Dto.OrderListResponse>>> getList(String queryString) {
        return testRestTemplate.exchange(
                ENDPOINT + queryString, HttpMethod.GET,
                new HttpEntity<>(fixture.adminHeaders()),
                new ParameterizedTypeReference<>() {}
        );
    }

    private HttpHeaders userHeaders() {
        return fixture.userHeaders(USER_LOGIN_ID, USER_PASSWORD);
    }
}
