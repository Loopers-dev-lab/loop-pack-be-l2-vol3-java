package com.loopers.interfaces.api.order;

import com.loopers.application.brand.BrandRequest;
import com.loopers.application.order.OrderRequest;
import com.loopers.application.product.ProductRequest;
import com.loopers.application.user.UserRequest;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.PageResponse;
import com.loopers.interfaces.api.brand.BrandAdminV1Dto;
import com.loopers.interfaces.api.product.ProductAdminV1Dto;
import com.loopers.interfaces.api.user.UserV1Dto;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class OrderApiE2ETest {

    private static final String ENDPOINT = "/api/v1/orders";
    private static final String BRAND_ENDPOINT = "/api-admin/v1/brands";
    private static final String PRODUCT_ENDPOINT = "/api-admin/v1/products";
    private static final String USER_ENDPOINT = "/api/v1/users";
    private static final String VALID_LDAP = "admin-ldap";
    private static final String USER_LOGIN_ID = "testuser";
    private static final String USER_PASSWORD = "Test1234!";

    @Autowired
    private TestRestTemplate testRestTemplate;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @Nested
    class 주문_요청 {

        @Test
        void 유효한_정보로_주문하면_200_응답과_생성된_주문_정보를_반환한다() {
            signUp();
            Long brandId = registerBrand("나이키", "스포츠 브랜드");
            Long productId1 = registerProduct(brandId, "운동화", new BigDecimal("50000"), 100, "편한 운동화");
            Long productId2 = registerProduct(brandId, "셔츠", new BigDecimal("30000"), 50, "멋진 셔츠");

            OrderRequest.Place request = new OrderRequest.Place(List.of(
                    new OrderRequest.PlaceItem(productId1, 2),
                    new OrderRequest.PlaceItem(productId2, 1)
            ));

            ResponseEntity<ApiResponse<OrderV1Dto.OrderResponse>> response = postOrder(request);

            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(response.getBody().data().id()).isNotNull(),
                    () -> assertThat(response.getBody().data().totalAmount()).isEqualByComparingTo(new BigDecimal("130000")),
                    () -> assertThat(response.getBody().data().orderItems()).hasSize(2),
                    () -> assertThat(response.getBody().data().createdAt()).isNotNull()
            );
        }

        @Test
        void 주문_시_스냅샷_정보가_올바르게_저장된다() {
            signUp();
            Long brandId = registerBrand("나이키", "스포츠 브랜드");
            Long productId = registerProduct(brandId, "운동화", new BigDecimal("50000"), 100, "편한 운동화");

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
            signUp();
            Long brandId = registerBrand("나이키", "스포츠 브랜드");
            Long productId = registerProduct(brandId, "운동화", new BigDecimal("50000"), 100, "편한 운동화");

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
            signUp();

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
                    () -> assertThat(response.getBody().meta().message()).contains("존재하지 않는 상품이 포함되어 있습니다")
            );
        }

        @Test
        void 재고가_부족하면_400_응답() {
            signUp();
            Long brandId = registerBrand("나이키", "스포츠 브랜드");
            Long productId = registerProduct(brandId, "운동화", new BigDecimal("50000"), 5, "편한 운동화");

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
                    () -> assertThat(response.getBody().meta().message()).contains("재고가 부족한 상품이 있습니다")
            );
        }

        @Test
        void 재고_부족_시_전체_주문이_실패하며_어떤_상품의_재고도_차감되지_않는다() {
            signUp();
            Long brandId = registerBrand("나이키", "스포츠 브랜드");
            Long productId1 = registerProduct(brandId, "운동화", new BigDecimal("50000"), 100, "편한 운동화");
            Long productId2 = registerProduct(brandId, "셔츠", new BigDecimal("30000"), 3, "멋진 셔츠");

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
            signUp();
            Long brandId = registerBrand("나이키", "스포츠 브랜드");
            Long productId = registerProduct(brandId, "운동화", new BigDecimal("50000"), 100, "편한 운동화");

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
        void 요청_필드_규칙_위반_시_400_응답() {
            signUp();

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
            signUp();
            Long brandId = registerBrand("나이키", "스포츠 브랜드");
            Long productId1 = registerProduct(brandId, "운동화", new BigDecimal("50000"), 100, "편한 운동화");
            Long productId2 = registerProduct(brandId, "셔츠", new BigDecimal("30000"), 100, "멋진 셔츠");

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
            signUp();
            signUp("otheruser", "Other1234!", "김철수", "other@example.com");
            Long brandId = registerBrand("나이키", "스포츠 브랜드");
            Long productId = registerProduct(brandId, "운동화", new BigDecimal("50000"), 100, "편한 운동화");

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
            signUp();
            Long brandId = registerBrand("나이키", "스포츠 브랜드");
            Long productId = registerProduct(brandId, "운동화", new BigDecimal("50000"), 100, "편한 운동화");
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
            signUp();
            Long brandId = registerBrand("나이키", "스포츠 브랜드");
            Long productId = registerProduct(brandId, "운동화", new BigDecimal("50000"), 100, "편한 운동화");
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
            signUp();
            Long brandId = registerBrand("나이키", "스포츠 브랜드");
            Long productId = registerProduct(brandId, "운동화", new BigDecimal("50000"), 100, "편한 운동화");
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
            signUp();

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
            signUp();

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
            signUp();

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
            signUp();
            Long brandId = registerBrand("나이키", "스포츠 브랜드");
            Long productId1 = registerProduct(brandId, "운동화", new BigDecimal("50000"), 100, "편한 운동화");
            Long productId2 = registerProduct(brandId, "셔츠", new BigDecimal("30000"), 100, "멋진 셔츠");

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
                    () -> assertThat(response.getBody().data().orderItems()).hasSize(2),
                    () -> assertThat(response.getBody().data().createdAt()).isNotNull()
            );
        }

        @Test
        void 주문_상품은_스냅샷_정보로_반환한다() {
            signUp();
            Long brandId = registerBrand("나이키", "스포츠 브랜드");
            Long productId = registerProduct(brandId, "운동화", new BigDecimal("50000"), 100, "편한 운동화");

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
            signUp();

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
            signUp();
            signUp("otheruser", "Other1234!", "김철수", "other@example.com");
            Long brandId = registerBrand("나이키", "스포츠 브랜드");
            Long productId = registerProduct(brandId, "운동화", new BigDecimal("50000"), 100, "편한 운동화");

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

    private void signUp() {
        signUp(USER_LOGIN_ID, USER_PASSWORD, "홍길동", "test@example.com");
    }

    private void signUp(String loginId, String password, String name, String email) {
        UserRequest.SignUp request = new UserRequest.SignUp(
                loginId, password, name,
                LocalDate.of(2000, 1, 15), email
        );
        testRestTemplate.exchange(
                USER_ENDPOINT, HttpMethod.POST, new HttpEntity<>(request),
                new ParameterizedTypeReference<ApiResponse<UserV1Dto.UserResponse>>() {}
        );
    }

    private Long registerBrand(String name, String description) {
        BrandRequest.Register request = new BrandRequest.Register(name, description);
        ResponseEntity<ApiResponse<BrandAdminV1Dto.BrandResponse>> response = testRestTemplate.exchange(
                BRAND_ENDPOINT, HttpMethod.POST,
                new HttpEntity<>(request, adminHeaders()),
                new ParameterizedTypeReference<>() {}
        );
        return response.getBody().data().id();
    }

    private Long registerProduct(Long brandId, String name, BigDecimal price, Integer stockQuantity, String description) {
        ProductRequest.Register request = new ProductRequest.Register(
                brandId, name, price, stockQuantity, description
        );
        ResponseEntity<ApiResponse<ProductAdminV1Dto.ProductResponse>> response = testRestTemplate.exchange(
                PRODUCT_ENDPOINT, HttpMethod.POST,
                new HttpEntity<>(request, adminHeaders()),
                new ParameterizedTypeReference<>() {}
        );
        return response.getBody().data().id();
    }

    private List<ProductAdminV1Dto.ProductResponse> getProductList() {
        ResponseEntity<ApiResponse<PageResponse<ProductAdminV1Dto.ProductResponse>>> response = testRestTemplate.exchange(
                PRODUCT_ENDPOINT, HttpMethod.GET,
                new HttpEntity<>(adminHeaders()),
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
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Loopers-LoginId", loginId);
        headers.set("X-Loopers-LoginPw", password);
        return testRestTemplate.exchange(
                ENDPOINT, HttpMethod.POST,
                new HttpEntity<>(request, headers),
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

    private HttpHeaders adminHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Loopers-Ldap", VALID_LDAP);
        return headers;
    }

    private HttpHeaders userHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Loopers-LoginId", USER_LOGIN_ID);
        headers.set("X-Loopers-LoginPw", USER_PASSWORD);
        return headers;
    }
}
