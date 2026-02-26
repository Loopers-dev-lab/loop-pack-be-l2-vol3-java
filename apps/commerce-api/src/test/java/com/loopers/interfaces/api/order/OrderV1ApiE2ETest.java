package com.loopers.interfaces.api.order;

import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.brand.BrandService;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductService;
import com.loopers.interfaces.api.ApiResponse;
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
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.web.util.UriComponentsBuilder;

import java.math.BigDecimal;
import java.util.List;

import static com.loopers.interfaces.api.ApiResponse.Metadata.Result;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(MySqlTestContainersConfig.class)
class OrderV1ApiE2ETest {

    private static final String ENDPOINT_ORDERS = "/api/v1/orders";
    private static final String LOGIN_ID = "orderuser";

    @Autowired
    private TestRestTemplate testRestTemplate;
    @Autowired
    private DatabaseCleanUp databaseCleanUp;
    @Autowired
    private BrandService brandService;
    @Autowired
    private ProductService productService;

    private Long productId;

    @BeforeEach
    void setUp() {
        UserV1Dto.SignUpRequest signUp = new UserV1Dto.SignUpRequest(
                "orderuser", "SecurePass1!", "order@example.com", "1990-01-15", "MALE");
        testRestTemplate.exchange("/api/v1/users", HttpMethod.POST, new HttpEntity<>(signUp),
                new ParameterizedTypeReference<ApiResponse<UserV1Dto.SignUpResponse>>() {
                });
        UserV1Dto.SignUpRequest otherSignUp = new UserV1Dto.SignUpRequest(
                "otheruser", "SecurePass1!", "other@example.com", "1992-05-20", "FEMALE");
        testRestTemplate.exchange("/api/v1/users", HttpMethod.POST, new HttpEntity<>(otherSignUp),
                new ParameterizedTypeReference<ApiResponse<UserV1Dto.SignUpResponse>>() {
                });
        BrandModel brand = brandService.register("E2E브랜드");
        ProductModel product = productService.register(brand.getId(), "E2E상품", new BigDecimal("10000"), 10);
        productId = product.getId();
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    private HttpHeaders authHeaders() {
        HttpHeaders h = new HttpHeaders();
        h.set("X-Loopers-LoginId", LOGIN_ID);
        return h;
    }

    @DisplayName("POST /api/v1/orders - 주문 생성")
    @Nested
    class CreateOrder {

        @Test
        void createOrder_withValidRequest_shouldReturn201() {
            OrderV1Dto.CreateOrderRequest request = new OrderV1Dto.CreateOrderRequest(
                    List.of(new OrderV1Dto.OrderItemRequest(productId, 2, null)));

            ResponseEntity<ApiResponse<OrderV1Dto.OrderResponse>> response = testRestTemplate.exchange(
                    ENDPOINT_ORDERS, HttpMethod.POST, new HttpEntity<>(request, authHeaders()),
                    new ParameterizedTypeReference<>() {
                    });

            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED),
                    () -> assertThat(response.getBody().meta().result()).isEqualTo(Result.SUCCESS),
                    () -> assertThat(response.getBody().data().id()).isNotNull(),
                    () -> assertThat(response.getBody().data().status()).isEqualTo("ORDERED"),
                    () -> assertThat(response.getBody().data().items()).hasSize(1),
                    () -> assertThat(response.getBody().data().items().get(0).productId()).isEqualTo(productId),
                    () -> assertThat(response.getBody().data().items().get(0).quantity()).isEqualTo(2));
        }

        @Test
        void createOrder_withoutLogin_shouldReturn401() {
            OrderV1Dto.CreateOrderRequest request = new OrderV1Dto.CreateOrderRequest(
                    List.of(new OrderV1Dto.OrderItemRequest(productId, 1, null)));

            ResponseEntity<ApiResponse<OrderV1Dto.OrderResponse>> response = testRestTemplate.exchange(
                    ENDPOINT_ORDERS, HttpMethod.POST, new HttpEntity<>(request), new ParameterizedTypeReference<>() {
                    });

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }
    }

    @DisplayName("GET /api/v1/orders/{orderId} - 주문 조회")
    @Nested
    class GetOrder {

        @Test
        void getOrder_withValidRequest_shouldReturn200() {
            OrderV1Dto.CreateOrderRequest createReq = new OrderV1Dto.CreateOrderRequest(
                    List.of(new OrderV1Dto.OrderItemRequest(productId, 1, null)));
            ResponseEntity<ApiResponse<OrderV1Dto.OrderResponse>> createRes = testRestTemplate.exchange(
                    ENDPOINT_ORDERS, HttpMethod.POST, new HttpEntity<>(createReq, authHeaders()),
                    new ParameterizedTypeReference<>() {
                    });
            Long orderId = createRes.getBody().data().id();

            ResponseEntity<ApiResponse<OrderV1Dto.OrderResponse>> response = testRestTemplate.exchange(
                    ENDPOINT_ORDERS + "/" + orderId, HttpMethod.GET, new HttpEntity<>(authHeaders()),
                    new ParameterizedTypeReference<>() {
                    });

            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(response.getBody().data().id()).isEqualTo(orderId),
                    () -> assertThat(response.getBody().data().status()).isEqualTo("ORDERED"));
        }

        @Test
        void getOrder_withWrongUser_shouldReturn404() {
            OrderV1Dto.CreateOrderRequest createReq = new OrderV1Dto.CreateOrderRequest(
                    List.of(new OrderV1Dto.OrderItemRequest(productId, 1, null)));
            ResponseEntity<ApiResponse<OrderV1Dto.OrderResponse>> createRes = testRestTemplate.exchange(
                    ENDPOINT_ORDERS, HttpMethod.POST, new HttpEntity<>(createReq, authHeaders()),
                    new ParameterizedTypeReference<>() {
                    });
            Long orderId = createRes.getBody().data().id();

            HttpHeaders otherUser = new HttpHeaders();
            otherUser.set("X-Loopers-LoginId", "otheruser");
            ResponseEntity<ApiResponse<OrderV1Dto.OrderResponse>> response = testRestTemplate.exchange(
                    ENDPOINT_ORDERS + "/" + orderId, HttpMethod.GET, new HttpEntity<>(otherUser),
                    new ParameterizedTypeReference<>() {
                    });

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }
    }

    @DisplayName("GET /api/v1/orders - 주문 목록")
    @Nested
    class GetOrders {

        @Test
        void getOrders_withValidRequest_shouldReturn200() {
            // 주문을 하나 생성해 두면 목록 조회 시 사용자·기간 조건이 동일하게 맞춰진다 (단일 테스트 실행 시에도 안정적)
            OrderV1Dto.CreateOrderRequest createReq = new OrderV1Dto.CreateOrderRequest(
                    List.of(new OrderV1Dto.OrderItemRequest(productId, 1, null)));
            testRestTemplate.exchange(
                    ENDPOINT_ORDERS, HttpMethod.POST, new HttpEntity<>(createReq, authHeaders()),
                    new ParameterizedTypeReference<ApiResponse<OrderV1Dto.OrderResponse>>() {});

            // + in query param is interpreted as space; use UTC (Z) to avoid encoding
            // issues
            String startStr = "2026-02-26T08:00:00.000Z";
            String endStr = "2026-02-26T10:00:00.000Z";
            String url = UriComponentsBuilder.fromUriString(ENDPOINT_ORDERS)
                    .queryParam("start", startStr)
                    .queryParam("end", endStr)
                    .queryParam("page", 0)
                    .queryParam("size", 20)
                    .build()
                    .toUriString();

            ResponseEntity<ApiResponse<List<OrderV1Dto.OrderResponse>>> response = testRestTemplate.exchange(
                    url, HttpMethod.GET, new HttpEntity<>(authHeaders()),
                    new ParameterizedTypeReference<>() {
                    });

            assertThat(response.getStatusCode())
                    .describedAs("status")
                    .isEqualTo(HttpStatus.OK);
            assertThat(response.getBody())
                    .describedAs("body (status=%s)", response.getStatusCode())
                    .isNotNull();
            assertThat(response.getBody().meta().result())
                    .describedAs("meta.result (message=%s)", response.getBody().meta().message())
                    .isEqualTo(Result.SUCCESS);
        }
    }

    @DisplayName("POST /api/v1/orders/{orderId}/cancel - 주문 취소")
    @Nested
    class CancelOrder {

        @Test
        void cancelOrder_withValidRequest_shouldReturn200() {
            OrderV1Dto.CreateOrderRequest createReq = new OrderV1Dto.CreateOrderRequest(
                    List.of(new OrderV1Dto.OrderItemRequest(productId, 1, null)));
            ResponseEntity<ApiResponse<OrderV1Dto.OrderResponse>> createRes = testRestTemplate.exchange(
                    ENDPOINT_ORDERS, HttpMethod.POST, new HttpEntity<>(createReq, authHeaders()),
                    new ParameterizedTypeReference<>() {
                    });
            Long orderId = createRes.getBody().data().id();

            ResponseEntity<ApiResponse<OrderV1Dto.OrderResponse>> response = testRestTemplate.exchange(
                    ENDPOINT_ORDERS + "/" + orderId + "/cancel", HttpMethod.POST, new HttpEntity<>(authHeaders()),
                    new ParameterizedTypeReference<>() {
                    });

            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(response.getBody().data().status()).isEqualTo("CANCELLED"));
        }

        @Test
        void cancelOrder_withoutLogin_shouldReturn401() {
            OrderV1Dto.CreateOrderRequest createReq = new OrderV1Dto.CreateOrderRequest(
                    List.of(new OrderV1Dto.OrderItemRequest(productId, 1, null)));
            ResponseEntity<ApiResponse<OrderV1Dto.OrderResponse>> createRes = testRestTemplate.exchange(
                    ENDPOINT_ORDERS, HttpMethod.POST, new HttpEntity<>(createReq, authHeaders()),
                    new ParameterizedTypeReference<>() {
                    });
            Long orderId = createRes.getBody().data().id();

            ResponseEntity<ApiResponse<OrderV1Dto.OrderResponse>> response = testRestTemplate.exchange(
                    ENDPOINT_ORDERS + "/" + orderId + "/cancel", HttpMethod.POST, new HttpEntity<>(null),
                    new ParameterizedTypeReference<>() {
                    });

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }
    }
}
