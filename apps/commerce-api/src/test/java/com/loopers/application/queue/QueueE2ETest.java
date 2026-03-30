package com.loopers.application.queue;

import com.loopers.application.order.OrderInfo;
import com.loopers.domain.brand.BrandService;
import com.loopers.domain.order.OrderRepository;
import com.loopers.domain.product.ProductService;
import com.loopers.domain.queue.EntryTokenService;
import com.loopers.domain.queue.WaitingQueueService;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.order.OrderV1Dto;
import com.loopers.utils.DatabaseCleanUp;
import com.loopers.utils.RedisCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.test.context.TestPropertySource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
        "queue.enabled=true",
        "queue.dynamic.enabled=false",
        "queue.dynamic.metric=hikari-pool-usage",
        "queue.dynamic.open-threshold=0.8",
        "queue.dynamic.close-threshold=0.5",
        "queue.dynamic.cooldown-seconds=30",
        "queue.dynamic.evaluation-interval-ms=999999"
})
@DisplayName("대기열 → 주문 E2E 테스트")
class QueueE2ETest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private QueueApp queueApp;

    @Autowired
    private WaitingQueueService waitingQueueService;

    @Autowired
    private EntryTokenService entryTokenService;

    @Autowired
    private BrandService brandService;

    @Autowired
    private ProductService productService;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @Autowired
    private RedisCleanUp redisCleanUp;

    @BeforeEach
    void setUp() {
        brandService.createBrand("testbrand", "테스트 브랜드");
        productService.createProduct("testprod1", "testbrand", "테스트 상품", new java.math.BigDecimal("10000"), 100);
    }

    @AfterEach
    void tearDown() {
        redisCleanUp.truncateAll();
        databaseCleanUp.truncateAllTables();
    }

    @Test
    @DisplayName("시나리오 4: 진입 → 토큰 발급 → 주문 성공 → 토큰 삭제 전체 흐름")
    void fullFlow_enter_issueToken_createOrder_consumeToken() {
        Long memberId = 1L;

        // 1. 대기열 진입
        QueueInfo enterInfo = queueApp.enterQueue(memberId);
        assertThat(enterInfo.status()).isEqualTo(QueueStatus.WAITING);
        assertThat(enterInfo.position()).isEqualTo(1L);

        // 2. 스케줄러로 토큰 발급 (popN + issue)
        List<Long> popped = waitingQueueService.popN(1);
        assertThat(popped).containsExactly(memberId);
        String token = entryTokenService.issue(memberId);

        // 3. 토큰으로 주문 생성
        OrderV1Dto.CreateOrderRequest orderRequest = new OrderV1Dto.CreateOrderRequest(
                memberId,
                List.of(new OrderV1Dto.OrderItemRequest("testprod1", 1)),
                null
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Entry-Token", token);

        ResponseEntity<ApiResponse<OrderV1Dto.OrderResponse>> response = restTemplate.exchange(
                "/api/v1/orders",
                HttpMethod.POST,
                new HttpEntity<>(orderRequest, headers),
                new ParameterizedTypeReference<>() {}
        );

        // 4. 주문 성공 확인
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().data().status()).isEqualTo("PENDING");

        // 5. 토큰 삭제 확인
        assertThat(entryTokenService.findToken(memberId)).isEmpty();
    }

    @Test
    @DisplayName("토큰 없이 주문 시도 → 403 FORBIDDEN")
    void createOrder_withoutToken_returns403() {
        // given
        OrderV1Dto.CreateOrderRequest orderRequest = new OrderV1Dto.CreateOrderRequest(
                1L,
                List.of(new OrderV1Dto.OrderItemRequest("testprod1", 1)),
                null
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        // when
        ResponseEntity<ApiResponse<Object>> response = restTemplate.exchange(
                "/api/v1/orders",
                HttpMethod.POST,
                new HttpEntity<>(orderRequest, headers),
                new ParameterizedTypeReference<>() {}
        );

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }
}
