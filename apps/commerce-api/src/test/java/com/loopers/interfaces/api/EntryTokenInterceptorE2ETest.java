package com.loopers.interfaces.api;

import com.loopers.application.queue.QueueProperties;
import com.loopers.application.queue.TokenService;
import com.loopers.config.TestRedisConfiguration;
import com.loopers.domain.brand.Brand;
import com.loopers.domain.brand.BrandRepository;
import com.loopers.domain.common.Money;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductRepository;
import com.loopers.domain.product.Stock;
import com.loopers.interfaces.api.order.OrderV1Dto;
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
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestRedisConfiguration.class)
@DisplayName("EntryTokenInterceptor E2E 테스트")
class EntryTokenInterceptorE2ETest {

    private static final String ORDER_ENDPOINT = "/api/v1/orders";

    @Autowired
    private TestRestTemplate testRestTemplate;

    @Autowired
    private TokenService tokenService;

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    @Autowired
    private QueueProperties queueProperties;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private BrandRepository brandRepository;

    private Long testUserId;
    private Product testProduct;

    @BeforeEach
    void setUp() {
        cleanUpRedisKeys();
        testUserId = 1L;

        // 테스트용 브랜드와 상품 생성
        Brand brand = brandRepository.save(Brand.create("Test Brand", "Test Description", null));
        testProduct = productRepository.save(Product.create(
            brand.getId(),
            "Test Product",
            "Test Description",
            new Money(10000),
            new Stock(100),
            null
        ));
    }

    @AfterEach
    void tearDown() {
        cleanUpRedisKeys();
        databaseCleanUp.truncateAllTables();
    }

    private void cleanUpRedisKeys() {
        Set<String> tokenKeys = redisTemplate.keys(queueProperties.tokenPrefix() + "*");
        if (tokenKeys != null && !tokenKeys.isEmpty()) {
            redisTemplate.delete(tokenKeys);
        }
    }

    @Nested
    @DisplayName("주문 API 토큰 검증")
    class OrderApiTokenValidation {

        @Test
        @DisplayName("유효한 토큰으로 주문 API에 접근하면 성공한다")
        void 유효한_토큰_접근_성공() {
            // Arrange
            String token = tokenService.issueToken(testUserId);

            HttpHeaders headers = new HttpHeaders();
            headers.set("X-User-Id", String.valueOf(testUserId));
            headers.set("X-Entry-Token", token);
            headers.set("Content-Type", "application/json");

            OrderV1Dto.PlaceOrderRequestDto request = new OrderV1Dto.PlaceOrderRequestDto(
                List.of(new OrderV1Dto.OrderItemRequestDto(testProduct.getId(), 1))
            );

            HttpEntity<OrderV1Dto.PlaceOrderRequestDto> entity = new HttpEntity<>(request, headers);

            // Act
            ParameterizedTypeReference<ApiResponse<OrderV1Dto.OrderResponseDto>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<OrderV1Dto.OrderResponseDto>> response =
                testRestTemplate.exchange(ORDER_ENDPOINT, HttpMethod.POST, entity, responseType);

            // Assert
            assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        }

        @Test
        @DisplayName("토큰 없이 주문 API에 접근하면 403이 반환된다")
        void 토큰_없이_접근_실패() {
            // Arrange
            HttpHeaders headers = new HttpHeaders();
            headers.set("X-User-Id", String.valueOf(testUserId));
            headers.set("Content-Type", "application/json");

            OrderV1Dto.PlaceOrderRequestDto request = new OrderV1Dto.PlaceOrderRequestDto(
                List.of(new OrderV1Dto.OrderItemRequestDto(testProduct.getId(), 1))
            );

            HttpEntity<OrderV1Dto.PlaceOrderRequestDto> entity = new HttpEntity<>(request, headers);

            // Act
            ResponseEntity<String> response =
                testRestTemplate.exchange(ORDER_ENDPOINT, HttpMethod.POST, entity, String.class);

            // Assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        }

        @Test
        @DisplayName("잘못된 토큰으로 주문 API에 접근하면 403이 반환된다")
        void 잘못된_토큰_접근_실패() {
            // Arrange
            tokenService.issueToken(testUserId);

            HttpHeaders headers = new HttpHeaders();
            headers.set("X-User-Id", String.valueOf(testUserId));
            headers.set("X-Entry-Token", "wrong-token");
            headers.set("Content-Type", "application/json");

            OrderV1Dto.PlaceOrderRequestDto request = new OrderV1Dto.PlaceOrderRequestDto(
                List.of(new OrderV1Dto.OrderItemRequestDto(testProduct.getId(), 1))
            );

            HttpEntity<OrderV1Dto.PlaceOrderRequestDto> entity = new HttpEntity<>(request, headers);

            // Act
            ResponseEntity<String> response =
                testRestTemplate.exchange(ORDER_ENDPOINT, HttpMethod.POST, entity, String.class);

            // Assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        }

        @Test
        @DisplayName("주문 완료 후 같은 토큰으로 재진입 시 실패한다")
        void 주문_완료_후_토큰_삭제_확인() {
            // Arrange: 토큰 발급 및 첫 번째 주문
            String token = tokenService.issueToken(testUserId);

            HttpHeaders headers = new HttpHeaders();
            headers.set("X-User-Id", String.valueOf(testUserId));
            headers.set("X-Entry-Token", token);
            headers.set("Content-Type", "application/json");

            OrderV1Dto.PlaceOrderRequestDto request = new OrderV1Dto.PlaceOrderRequestDto(
                List.of(new OrderV1Dto.OrderItemRequestDto(testProduct.getId(), 1))
            );

            HttpEntity<OrderV1Dto.PlaceOrderRequestDto> entity = new HttpEntity<>(request, headers);

            // 첫 번째 주문
            testRestTemplate.exchange(ORDER_ENDPOINT, HttpMethod.POST, entity, String.class);

            // Act: 같은 토큰으로 재주문 시도
            ResponseEntity<String> response =
                testRestTemplate.exchange(ORDER_ENDPOINT, HttpMethod.POST, entity, String.class);

            // Assert: 토큰이 삭제되어 403 반환
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        }
    }

    @Nested
    @DisplayName("주문 조회 API는 토큰 검증 제외")
    class OrderGetApiNoTokenRequired {

        @Test
        @DisplayName("주문 조회 API는 토큰 없이도 접근 가능하다")
        void 조회_API_토큰_불필요() {
            // Arrange
            HttpHeaders headers = new HttpHeaders();
            headers.set("X-User-Id", String.valueOf(testUserId));

            HttpEntity<Void> entity = new HttpEntity<>(headers);

            // Act
            ResponseEntity<String> response =
                testRestTemplate.exchange(ORDER_ENDPOINT, HttpMethod.GET, entity, String.class);

            // Assert: 403이 아닌 다른 응답 (2xx 또는 4xx 중 403이 아닌 것)
            assertThat(response.getStatusCode()).isNotEqualTo(HttpStatus.FORBIDDEN);
        }
    }
}