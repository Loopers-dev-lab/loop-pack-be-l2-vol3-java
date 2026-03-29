package com.loopers.interfaces.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import com.loopers.domain.brand.Brand;
import com.loopers.domain.common.Money;
import com.loopers.domain.product.Product;
import com.loopers.domain.stock.Stock;
import com.loopers.infrastructure.brand.BrandJpaRepository;
import com.loopers.infrastructure.product.ProductJpaRepository;
import com.loopers.infrastructure.stock.StockJpaRepository;
import com.loopers.utils.DatabaseCleanUp;
import java.util.List;
import java.util.Map;
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
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class OrderV1ApiE2ETest {

    private static final String ENDPOINT = "/api/v1/orders";
    private static final String USERS_ENDPOINT = "/api/v1/users";
    private static final String LOGIN_ID = "testuser";
    private static final String PASSWORD = "Test1234!";

    private final TestRestTemplate testRestTemplate;
    private final DatabaseCleanUp databaseCleanUp;
    private final BrandJpaRepository brandJpaRepository;
    private final ProductJpaRepository productJpaRepository;
    private final StockJpaRepository stockJpaRepository;

    @Autowired
    public OrderV1ApiE2ETest(
        TestRestTemplate testRestTemplate,
        DatabaseCleanUp databaseCleanUp,
        BrandJpaRepository brandJpaRepository,
        ProductJpaRepository productJpaRepository,
        StockJpaRepository stockJpaRepository
    ) {
        this.testRestTemplate = testRestTemplate;
        this.databaseCleanUp = databaseCleanUp;
        this.brandJpaRepository = brandJpaRepository;
        this.productJpaRepository = productJpaRepository;
        this.stockJpaRepository = stockJpaRepository;
    }

    @BeforeEach
    void setUp() {
        Map<String, String> signUpRequest = Map.of(
            "loginId", LOGIN_ID,
            "password", PASSWORD,
            "name", "홍길동",
            "birthDate", "19900101",
            "email", "test@example.com"
        );
        testRestTemplate.exchange(
            USERS_ENDPOINT,
            HttpMethod.POST,
            new HttpEntity<>(signUpRequest),
            new ParameterizedTypeReference<ApiResponse<Map<String, String>>>() {}
        );
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("POST /api/v1/orders (주문 생성)")
    @Nested
    class CreateOrder {

        @DisplayName("모든 재고가 충분하면, 201 Created와 주문 정보를 반환한다.")
        @Test
        void returnsCreated_whenAllStocksAreSufficient() {
            // arrange
            Brand brand = brandJpaRepository.save(new Brand("나이키"));
            Product productA = productJpaRepository.save(new Product(brand.getId(), "신발A", new Money(50000L), "설명A"));
            Product productB = productJpaRepository.save(new Product(brand.getId(), "신발B", new Money(30000L), "설명B"));
            stockJpaRepository.save(new Stock(productA.getId(), 100L));
            stockJpaRepository.save(new Stock(productB.getId(), 50L));

            HttpHeaders headers = new HttpHeaders();
            headers.set("X-Loopers-LoginId", LOGIN_ID);
            headers.set("X-Loopers-LoginPw", PASSWORD);
            headers.set("Content-Type", "application/json");

            Map<String, Object> request = Map.of(
                "items", List.of(
                    Map.of("productId", productA.getId(), "quantity", 2),
                    Map.of("productId", productB.getId(), "quantity", 3)
                ),
                "cardType", "SAMSUNG",
                "cardNo", "1234-5678-9012-3456",
                "updateDefaultCard", false
            );

            // act
            ResponseEntity<ApiResponse<Map<String, Object>>> response = testRestTemplate.exchange(
                ENDPOINT,
                HttpMethod.POST,
                new HttpEntity<>(request, headers),
                new ParameterizedTypeReference<>() {}
            );

            // assert
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED),
                () -> assertThat(response.getBody()).isNotNull(),
                () -> {
                    Map<String, Object> data = response.getBody().data();
                    assertThat(data).isNotNull();
                    assertThat(data.get("orderId")).isNotNull();
                    assertThat(data.get("status")).isEqualTo("CREATED");
                    assertThat(((Number) data.get("totalAmount")).longValue())
                        .isEqualTo(50000L * 2 + 30000L * 3); // 190000
                }
            );
        }

        @DisplayName("재고가 부족한 상품이 있으면, 400 Bad Request를 반환한다.")
        @Test
        void returnsBadRequest_whenAnyStockIsInsufficient() {
            // arrange
            Brand brand = brandJpaRepository.save(new Brand("나이키"));
            Product product = productJpaRepository.save(new Product(brand.getId(), "신발", new Money(50000L), "설명"));
            stockJpaRepository.save(new Stock(product.getId(), 5L)); // 재고 5개

            HttpHeaders headers = new HttpHeaders();
            headers.set("X-Loopers-LoginId", LOGIN_ID);
            headers.set("X-Loopers-LoginPw", PASSWORD);
            headers.set("Content-Type", "application/json");

            Map<String, Object> request = Map.of(
                "items", List.of(Map.of("productId", product.getId(), "quantity", 10)),
                "cardType", "SAMSUNG",
                "cardNo", "1234-5678-9012-3456",
                "updateDefaultCard", false
            );

            // act
            ResponseEntity<ApiResponse<Map<String, Object>>> response = testRestTemplate.exchange(
                ENDPOINT,
                HttpMethod.POST,
                new HttpEntity<>(request, headers),
                new ParameterizedTypeReference<>() {}
            );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @DisplayName("잘못된 비밀번호로 요청하면, 401 Unauthorized를 반환한다.")
        @Test
        void returnsUnauthorized_whenPasswordIsWrong() {
            // arrange
            HttpHeaders headers = new HttpHeaders();
            headers.set("X-Loopers-LoginId", LOGIN_ID);
            headers.set("X-Loopers-LoginPw", "WrongPass1!");
            headers.set("Content-Type", "application/json");

            Map<String, Object> request = Map.of(
                "items", List.of(Map.of("productId", 1L, "quantity", 1)),
                "cardType", "SAMSUNG",
                "cardNo", "1234-5678-9012-3456",
                "updateDefaultCard", false
            );

            // act
            ResponseEntity<ApiResponse<Map<String, Object>>> response = testRestTemplate.exchange(
                ENDPOINT,
                HttpMethod.POST,
                new HttpEntity<>(request, headers),
                new ParameterizedTypeReference<>() {}
            );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }

        @DisplayName("존재하지 않는 상품 ID로 주문하면, 404 Not Found를 반환한다.")
        @Test
        void returnsNotFound_whenProductDoesNotExist() {
            // arrange
            HttpHeaders headers = new HttpHeaders();
            headers.set("X-Loopers-LoginId", LOGIN_ID);
            headers.set("X-Loopers-LoginPw", PASSWORD);
            headers.set("Content-Type", "application/json");

            Map<String, Object> request = Map.of(
                "items", List.of(Map.of("productId", 999999L, "quantity", 1)),
                "cardType", "SAMSUNG",
                "cardNo", "1234-5678-9012-3456",
                "updateDefaultCard", false
            );

            // act
            ResponseEntity<ApiResponse<Map<String, Object>>> response = testRestTemplate.exchange(
                ENDPOINT,
                HttpMethod.POST,
                new HttpEntity<>(request, headers),
                new ParameterizedTypeReference<>() {}
            );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }
    }

    @DisplayName("GET /api/v1/orders (유저 주문 목록 조회)")
    @Nested
    class GetOrderList {

        @DisplayName("인증 성공 시, 200 OK와 본인의 주문 목록을 반환한다.")
        @Test
        void returnsOrderList_whenAuthIsValid() {
            // arrange - 주문 2건 생성
            Brand brand = brandJpaRepository.save(new Brand("나이키"));
            Product product = productJpaRepository.save(new Product(brand.getId(), "신발", new Money(50000L), "설명"));
            stockJpaRepository.save(new Stock(product.getId(), 100L));

            HttpHeaders headers = new HttpHeaders();
            headers.set("X-Loopers-LoginId", LOGIN_ID);
            headers.set("X-Loopers-LoginPw", PASSWORD);
            headers.set("Content-Type", "application/json");

            Map<String, Object> orderRequest = Map.of(
                "items", List.of(Map.of("productId", product.getId(), "quantity", 1)),
                "cardType", "SAMSUNG", "cardNo", "1234-5678-9012-3456", "updateDefaultCard", false
            );
            testRestTemplate.exchange(ENDPOINT, HttpMethod.POST, new HttpEntity<>(orderRequest, headers), new ParameterizedTypeReference<ApiResponse<Map<String, Object>>>() {});
            testRestTemplate.exchange(ENDPOINT, HttpMethod.POST, new HttpEntity<>(orderRequest, headers), new ParameterizedTypeReference<ApiResponse<Map<String, Object>>>() {});

            // act
            ResponseEntity<ApiResponse<Map<String, Object>>> response = testRestTemplate.exchange(
                ENDPOINT,
                HttpMethod.GET,
                new HttpEntity<>(headers),
                new ParameterizedTypeReference<>() {}
            );

            // assert
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> {
                    Map<String, Object> data = response.getBody().data();
                    assertThat(data).isNotNull();
                    List<?> content = (List<?>) data.get("content");
                    assertThat(content).hasSize(2);
                }
            );
        }

        @DisplayName("주문이 없는 유저 조회 시, 200 OK와 빈 목록을 반환한다.")
        @Test
        void returnsEmptyList_whenNoOrders() {
            // arrange
            HttpHeaders headers = new HttpHeaders();
            headers.set("X-Loopers-LoginId", LOGIN_ID);
            headers.set("X-Loopers-LoginPw", PASSWORD);

            // act
            ResponseEntity<ApiResponse<Map<String, Object>>> response = testRestTemplate.exchange(
                ENDPOINT,
                HttpMethod.GET,
                new HttpEntity<>(headers),
                new ParameterizedTypeReference<>() {}
            );

            // assert
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> {
                    List<?> content = (List<?>) response.getBody().data().get("content");
                    assertThat(content).isEmpty();
                }
            );
        }

        @DisplayName("잘못된 비밀번호로 요청하면, 401 Unauthorized를 반환한다.")
        @Test
        void returnsUnauthorized_whenPasswordIsWrong() {
            // arrange
            HttpHeaders headers = new HttpHeaders();
            headers.set("X-Loopers-LoginId", LOGIN_ID);
            headers.set("X-Loopers-LoginPw", "WrongPass1!");

            // act
            ResponseEntity<ApiResponse<Map<String, Object>>> response = testRestTemplate.exchange(
                ENDPOINT,
                HttpMethod.GET,
                new HttpEntity<>(headers),
                new ParameterizedTypeReference<>() {}
            );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }
    }

    @DisplayName("GET /api/v1/orders/{orderId} (주문 단건 상세 조회)")
    @Nested
    class GetOrderDetail {

        @DisplayName("본인 주문 조회 시, 200 OK와 주문 아이템을 포함한 상세를 반환한다.")
        @Test
        void returnsOrderDetail_whenOrderBelongsToUser() {
            // arrange - 주문 생성
            Brand brand = brandJpaRepository.save(new Brand("나이키"));
            Product product = productJpaRepository.save(new Product(brand.getId(), "신발", new Money(50000L), "설명"));
            stockJpaRepository.save(new Stock(product.getId(), 100L));

            HttpHeaders headers = new HttpHeaders();
            headers.set("X-Loopers-LoginId", LOGIN_ID);
            headers.set("X-Loopers-LoginPw", PASSWORD);
            headers.set("Content-Type", "application/json");

            Map<String, Object> orderRequest = Map.of(
                "items", List.of(Map.of("productId", product.getId(), "quantity", 2)),
                "cardType", "SAMSUNG", "cardNo", "1234-5678-9012-3456", "updateDefaultCard", false
            );
            ResponseEntity<ApiResponse<Map<String, Object>>> createResponse = testRestTemplate.exchange(
                ENDPOINT, HttpMethod.POST, new HttpEntity<>(orderRequest, headers), new ParameterizedTypeReference<>() {}
            );
            Long orderId = ((Number) createResponse.getBody().data().get("orderId")).longValue();

            // act
            ResponseEntity<ApiResponse<Map<String, Object>>> response = testRestTemplate.exchange(
                ENDPOINT + "/" + orderId,
                HttpMethod.GET,
                new HttpEntity<>(headers),
                new ParameterizedTypeReference<>() {}
            );

            // assert
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> {
                    Map<String, Object> data = response.getBody().data();
                    assertThat(((Number) data.get("orderId")).longValue()).isEqualTo(orderId);
                    assertThat(data.get("status")).isEqualTo("CREATED");
                    assertThat(((Number) data.get("totalAmount")).longValue()).isEqualTo(100000L);
                    List<?> items = (List<?>) data.get("items");
                    assertThat(items).hasSize(1);
                }
            );
        }

        @DisplayName("존재하지 않는 주문 ID로 조회 시, 404 Not Found를 반환한다.")
        @Test
        void returnsNotFound_whenOrderDoesNotExist() {
            // arrange
            HttpHeaders headers = new HttpHeaders();
            headers.set("X-Loopers-LoginId", LOGIN_ID);
            headers.set("X-Loopers-LoginPw", PASSWORD);

            // act
            ResponseEntity<ApiResponse<Map<String, Object>>> response = testRestTemplate.exchange(
                ENDPOINT + "/999999",
                HttpMethod.GET,
                new HttpEntity<>(headers),
                new ParameterizedTypeReference<>() {}
            );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }

        @DisplayName("잘못된 비밀번호로 요청하면, 401 Unauthorized를 반환한다.")
        @Test
        void returnsUnauthorized_whenPasswordIsWrong() {
            // arrange
            HttpHeaders headers = new HttpHeaders();
            headers.set("X-Loopers-LoginId", LOGIN_ID);
            headers.set("X-Loopers-LoginPw", "WrongPass1!");

            // act
            ResponseEntity<ApiResponse<Map<String, Object>>> response = testRestTemplate.exchange(
                ENDPOINT + "/1",
                HttpMethod.GET,
                new HttpEntity<>(headers),
                new ParameterizedTypeReference<>() {}
            );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }
    }
}
