package com.loopers.interfaces.api.order;

import com.loopers.application.user.UserFacade;
import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.brand.BrandService;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductService;
import com.loopers.domain.queue.EntryTokenRepository;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.user.UserV1Dto;
import com.loopers.testcontainers.MySqlTestContainersConfig;
import com.loopers.testcontainers.RedisTestContainersConfig;
import com.loopers.utils.DatabaseCleanUp;
import com.loopers.utils.RedisCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
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
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.util.List;

import static com.loopers.interfaces.api.ApiResponse.Metadata.Result;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

/**
 * {@code queue.order.require-entry-token=true} 일 때 주문 API 입장 토큰 관문 E2E.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.task.scheduling.enabled=false",
                "queue.order.require-entry-token=true"
        }
)
@Import({MySqlTestContainersConfig.class, RedisTestContainersConfig.class})
class OrderV1ApiEntryTokenE2ETest {

    private static final String ENDPOINT_ORDERS = "/api/v1/orders";
    /** userId 규칙: 최대 10자 (회원가입 검증 통과) */
    private static final String LOGIN_ID = "entokusr01";

    @Autowired
    private TestRestTemplate testRestTemplate;
    @Autowired
    private DatabaseCleanUp databaseCleanUp;
    @Autowired
    private RedisCleanUp redisCleanUp;
    @Autowired
    private BrandService brandService;
    @Autowired
    private ProductService productService;
    @Autowired
    private UserFacade userFacade;
    @Autowired
    private EntryTokenRepository entryTokenRepository;

    private Long productId;

    @BeforeEach
    void setUp() {
        UserV1Dto.SignUpRequest signUp = new UserV1Dto.SignUpRequest(
                LOGIN_ID, "SecurePass1!", "entok@example.com", "1990-01-15", "MALE");
        HttpHeaders signHeaders = new HttpHeaders();
        signHeaders.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<ApiResponse<UserV1Dto.SignUpResponse>> signRes = testRestTemplate.exchange(
                "/api/v1/users", HttpMethod.POST, new HttpEntity<>(signUp, signHeaders),
                new ParameterizedTypeReference<>() {
                });
        assertThat(signRes.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        BrandModel brand = brandService.registerBrand("EntryToken브랜드");
        ProductModel product = productService.registerProduct(brand.getId(), "EntryToken상품", new BigDecimal("10000"), 10);
        productId = product.getId();
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
        redisCleanUp.truncateAll();
    }

    private HttpHeaders authHeaders() {
        HttpHeaders h = new HttpHeaders();
        h.set("X-Loopers-LoginId", LOGIN_ID);
        h.setContentType(MediaType.APPLICATION_JSON);
        return h;
    }

    @Test
    @DisplayName("입장 토큰 없이 주문하면 400")
    void createOrder_withoutEntryToken_shouldReturn400() {
        OrderV1Dto.CreateOrderRequest request = new OrderV1Dto.CreateOrderRequest(
                List.of(new OrderV1Dto.OrderItemRequest(productId, 1, null)), null);

        ResponseEntity<ApiResponse<OrderV1Dto.OrderResponse>> response = testRestTemplate.exchange(
                ENDPOINT_ORDERS, HttpMethod.POST, new HttpEntity<>(request, authHeaders()),
                new ParameterizedTypeReference<>() {
                });

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().meta().result()).isEqualTo(Result.FAIL);
    }

    @Test
    @DisplayName("잘못된 입장 토큰이면 400")
    void createOrder_withWrongEntryToken_shouldReturn400() {
        Long userId = userFacade.findUserIdByLoginId(LOGIN_ID).orElseThrow();
        entryTokenRepository.saveEntryToken(userId, "good-token", 300L);

        HttpHeaders h = authHeaders();
        h.set("X-Entry-Token", "bad-token");
        OrderV1Dto.CreateOrderRequest request = new OrderV1Dto.CreateOrderRequest(
                List.of(new OrderV1Dto.OrderItemRequest(productId, 1, null)), null);

        ResponseEntity<ApiResponse<OrderV1Dto.OrderResponse>> response = testRestTemplate.exchange(
                ENDPOINT_ORDERS, HttpMethod.POST, new HttpEntity<>(request, h),
                new ParameterizedTypeReference<>() {
                });

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(entryTokenRepository.findEntryToken(userId)).contains("good-token");
    }

    @Test
    @DisplayName("유효한 입장 토큰이면 주문 성공 후 토큰은 소모된다")
    void createOrder_withValidEntryToken_shouldReturn201AndConsumeToken() {
        Long userId = userFacade.findUserIdByLoginId(LOGIN_ID).orElseThrow();
        entryTokenRepository.saveEntryToken(userId, "valid-token", 300L);

        HttpHeaders h = authHeaders();
        h.set("X-Entry-Token", "valid-token");
        OrderV1Dto.CreateOrderRequest request = new OrderV1Dto.CreateOrderRequest(
                List.of(new OrderV1Dto.OrderItemRequest(productId, 1, null)), null);

        ResponseEntity<ApiResponse<OrderV1Dto.OrderResponse>> response = testRestTemplate.exchange(
                ENDPOINT_ORDERS, HttpMethod.POST, new HttpEntity<>(request, h),
                new ParameterizedTypeReference<>() {
                });

        assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED),
                () -> assertThat(response.getBody().meta().result()).isEqualTo(Result.SUCCESS),
                () -> assertThat(response.getBody().data().status()).isEqualTo("ORDERED")
        );
        assertThat(entryTokenRepository.findEntryToken(userId)).isEmpty();
    }
}
