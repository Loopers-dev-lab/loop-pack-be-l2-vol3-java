package com.loopers.interfaces.api.queue;

import com.loopers.domain.brand.Brand;
import com.loopers.domain.brand.BrandStatus;
import com.loopers.domain.member.BirthDate;
import com.loopers.domain.member.Email;
import com.loopers.domain.member.LoginId;
import com.loopers.domain.member.Member;
import com.loopers.domain.member.MemberName;
import com.loopers.domain.point.Point;
import com.loopers.domain.product.MarginType;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductOption;
import com.loopers.domain.product.ProductStatus;
import com.loopers.domain.queue.FeatureFlag;
import com.loopers.domain.queue.QueueConstants;
import com.loopers.domain.queue.QueueService;
import com.loopers.application.queue.QueueScheduler;
import com.loopers.domain.queue.QueueTokenService;
import com.loopers.domain.queue.SchedulerLock;
import com.loopers.infrastructure.brand.BrandJpaRepository;
import com.loopers.infrastructure.member.MemberJpaRepository;
import com.loopers.infrastructure.point.PointJpaRepository;
import com.loopers.infrastructure.product.ProductJpaRepository;
import com.loopers.infrastructure.product.ProductOptionJpaRepository;
import com.loopers.infrastructure.queue.FeatureFlagJpaRepository;
import com.loopers.infrastructure.queue.SchedulerLockJpaRepository;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.order.dto.OrderV1Dto;
import com.loopers.interfaces.api.queue.dto.QueueV1Dto;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static com.loopers.config.redis.RedisConfig.REDIS_TEMPLATE_MASTER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

// [E2E 테스트]
//
// 대상: 대기열 + 주문 API 전체 흐름
//
// 테스트 범위: HTTP 요청 -> Controller -> Facade -> Service -> Repository -> Redis/Database
// 사용 라이브러리: JUnit 5, Spring Boot Test, TestRestTemplate, Testcontainers, AssertJ
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DisplayName("QueueV1Api E2E 테스트")
class QueueV1ApiE2ETest {

    private static final String ENDPOINT_QUEUE_ENTER = "/api/v1/queue/enter";
    private static final String ENDPOINT_QUEUE_POSITION = "/api/v1/queue/position";
    private static final String ENDPOINT_ORDERS = "/api/v1/orders";
    private static final String HEADER_LOGIN_ID = "X-Loopers-LoginId";
    private static final String HEADER_LOGIN_PW = "X-Loopers-LoginPw";

    @MockitoBean
    private com.loopers.infrastructure.payment.PgFeignClient pgFeignClient;

    // 백그라운드 스케줄러 비활성화 (다른 테스트 컨텍스트와의 Redis 간섭 방지)
    @MockitoBean
    private QueueScheduler queueScheduler;

    private final TestRestTemplate testRestTemplate;
    private final MemberJpaRepository memberJpaRepository;
    private final BrandJpaRepository brandJpaRepository;
    private final ProductJpaRepository productJpaRepository;
    private final ProductOptionJpaRepository productOptionJpaRepository;
    private final PointJpaRepository pointJpaRepository;
    private final FeatureFlagJpaRepository featureFlagJpaRepository;
    private final SchedulerLockJpaRepository schedulerLockJpaRepository;
    private final QueueTokenService queueTokenService;
    private final QueueService queueService;
    private final DatabaseCleanUp databaseCleanUp;
    private final PasswordEncoder passwordEncoder;
    private final RedisTemplate<String, String> redisTemplate;

    @Autowired
    public QueueV1ApiE2ETest(
            TestRestTemplate testRestTemplate,
            MemberJpaRepository memberJpaRepository,
            BrandJpaRepository brandJpaRepository,
            ProductJpaRepository productJpaRepository,
            ProductOptionJpaRepository productOptionJpaRepository,
            PointJpaRepository pointJpaRepository,
            FeatureFlagJpaRepository featureFlagJpaRepository,
            SchedulerLockJpaRepository schedulerLockJpaRepository,
            QueueTokenService queueTokenService,
            QueueService queueService,
            DatabaseCleanUp databaseCleanUp,
            PasswordEncoder passwordEncoder,
            @Qualifier(REDIS_TEMPLATE_MASTER) RedisTemplate<String, String> redisTemplate) {
        this.testRestTemplate = testRestTemplate;
        this.memberJpaRepository = memberJpaRepository;
        this.brandJpaRepository = brandJpaRepository;
        this.productJpaRepository = productJpaRepository;
        this.productOptionJpaRepository = productOptionJpaRepository;
        this.pointJpaRepository = pointJpaRepository;
        this.featureFlagJpaRepository = featureFlagJpaRepository;
        this.schedulerLockJpaRepository = schedulerLockJpaRepository;
        this.queueTokenService = queueTokenService;
        this.queueService = queueService;
        this.databaseCleanUp = databaseCleanUp;
        this.passwordEncoder = passwordEncoder;
        this.redisTemplate = redisTemplate;
    }

    @BeforeEach
    void setUp() {
        // 테스트 간 Feature Flag 캐시 오염 방지
        queueService.resetCache();

        // 스케줄러 락 초기 데이터
        if (schedulerLockJpaRepository.count() == 0) {
            schedulerLockJpaRepository.save(new SchedulerLock("QUEUE_SCHEDULER"));
        }

        // PG 결제 요청 stub
        when(pgFeignClient.requestPayment(anyString(), any()))
                .thenReturn(new com.loopers.infrastructure.payment.PgPaymentResponse(
                        "20250319:TR:test123", "1", "ACCEPTED", null));
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
        cleanupRedis();
    }

    private void cleanupRedis() {
        redisTemplate.delete(QueueConstants.QUEUE_KEY);
        Set<String> tokenKeys = redisTemplate.keys(QueueConstants.TOKEN_KEY_PREFIX + "*");
        if (tokenKeys != null && !tokenKeys.isEmpty()) {
            redisTemplate.delete(tokenKeys);
        }
    }

    private Member saveMember(String loginId) {
        Member member = new Member(
                new LoginId(loginId),
                passwordEncoder.encode("Password1!"),
                new MemberName("테스터"),
                new Email(loginId + "@example.com"),
                new BirthDate("19900101"));
        return memberJpaRepository.save(member);
    }

    private void saveQueueFlag(boolean enabled) {
        featureFlagJpaRepository.save(new FeatureFlag("QUEUE_ENABLED", enabled));
    }

    private Brand saveActiveBrand() {
        Brand brand = new Brand("테스트브랜드", "브랜드 설명");
        brand.changeStatus(BrandStatus.ACTIVE);
        return brandJpaRepository.save(brand);
    }

    private Product saveProduct(Brand brand) {
        Product product = new Product(brand, "테스트상품", 10000, 8000, 1000, 2500,
                "상품 설명", MarginType.AMOUNT, ProductStatus.ON_SALE, "Y", List.of());
        return productJpaRepository.save(product);
    }

    private ProductOption saveProductOption(Long productId) {
        ProductOption option = new ProductOption(productId, "기본 옵션", 100);
        return productOptionJpaRepository.save(option);
    }

    private HttpHeaders memberAuthHeaders(String loginId) {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HEADER_LOGIN_ID, loginId);
        headers.set(HEADER_LOGIN_PW, "Password1!");
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    private OrderV1Dto.CreateOrderRequest createOrderRequest(Long productId, Long optionId) {
        return new OrderV1Dto.CreateOrderRequest(
                List.of(new OrderV1Dto.OrderItemRequest(productId, optionId, 1)),
                null, 0, List.of(), "SAMSUNG", "1234-5678-9012-3456");
    }

    @Nested
    @DisplayName("전체 흐름: 대기열 진입 -> 순번 조회 -> 토큰 발급 -> 주문")
    class FullFlow {

        @Test
        @DisplayName("대기열 진입 후 토큰을 받으면 주문에 성공한다")
        void queue_enter_then_token_then_order_success() {
            // given
            saveQueueFlag(true);
            Member member = saveMember("testuser1");
            Brand brand = saveActiveBrand();
            Product product = saveProduct(brand);
            ProductOption option = saveProductOption(product.getId());
            pointJpaRepository.save(Point.create(member.getId(), 50000));

            HttpHeaders headers = memberAuthHeaders("testuser1");

            // when: 1. 대기열 진입
            ResponseEntity<ApiResponse<QueueV1Dto.EnterResponse>> enterResponse = testRestTemplate.exchange(
                    ENDPOINT_QUEUE_ENTER,
                    HttpMethod.POST,
                    new HttpEntity<>(headers),
                    new ParameterizedTypeReference<>() {});

            // then: 순번 반환
            assertAll(
                    () -> assertThat(enterResponse.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(enterResponse.getBody()).isNotNull(),
                    () -> assertThat(enterResponse.getBody().data().position()).isGreaterThan(0));

            // when: 2. 토큰 수동 발급 (스케줄러는 MockBean으로 비활성화)
            queueTokenService.issueToken(member.getId());

            // when: 3. 순번 조회 (토큰 발급 후)
            ResponseEntity<ApiResponse<QueueV1Dto.PositionResponse>> readyResponse = testRestTemplate.exchange(
                    ENDPOINT_QUEUE_POSITION,
                    HttpMethod.GET,
                    new HttpEntity<>(headers),
                    new ParameterizedTypeReference<>() {});

            // then: 입장 가능 상태 (토큰 포함)
            assertAll(
                    () -> assertThat(readyResponse.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(readyResponse.getBody().data().position()).isEqualTo(0),
                    () -> assertThat(readyResponse.getBody().data().token()).isNotNull());

            // when: 4. 주문 생성
            OrderV1Dto.CreateOrderRequest orderRequest = createOrderRequest(product.getId(), option.getId());
            ResponseEntity<ApiResponse<OrderV1Dto.OrderDetailResponse>> orderResponse = testRestTemplate.exchange(
                    ENDPOINT_ORDERS,
                    HttpMethod.POST,
                    new HttpEntity<>(orderRequest, headers),
                    new ParameterizedTypeReference<>() {});

            // then: 주문 성공
            assertAll(
                    () -> assertThat(orderResponse.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(orderResponse.getBody()).isNotNull(),
                    () -> assertThat(orderResponse.getBody().data().id()).isNotNull());
        }
    }

    @Nested
    @DisplayName("토큰 없이 주문 시도 (flag ON)")
    class OrderWithoutToken {

        @Test
        @DisplayName("대기열 활성 상태에서 토큰 없이 주문하면 차단된다")
        void order_without_token_blocked() {
            // given
            saveQueueFlag(true);
            Member member = saveMember("testuser1");
            Brand brand = saveActiveBrand();
            Product product = saveProduct(brand);
            ProductOption option = saveProductOption(product.getId());
            pointJpaRepository.save(Point.create(member.getId(), 50000));

            HttpHeaders headers = memberAuthHeaders("testuser1");
            OrderV1Dto.CreateOrderRequest orderRequest = createOrderRequest(product.getId(), option.getId());

            // when: 토큰 없이 바로 주문
            ResponseEntity<ApiResponse<OrderV1Dto.OrderDetailResponse>> response = testRestTemplate.exchange(
                    ENDPOINT_ORDERS,
                    HttpMethod.POST,
                    new HttpEntity<>(orderRequest, headers),
                    new ParameterizedTypeReference<>() {});

            // then: 차단 (400 BAD_REQUEST)
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }
    }

    @Nested
    @DisplayName("flag OFF 시 대기열 없이 주문")
    class OrderWithFlagOff {

        @Test
        @DisplayName("대기열 비활성 상태에서는 토큰 없이도 주문에 성공한다")
        void order_without_queue_success() {
            // given
            saveQueueFlag(false);
            Member member = saveMember("testuser1");
            Brand brand = saveActiveBrand();
            Product product = saveProduct(brand);
            ProductOption option = saveProductOption(product.getId());
            pointJpaRepository.save(Point.create(member.getId(), 50000));

            HttpHeaders headers = memberAuthHeaders("testuser1");
            OrderV1Dto.CreateOrderRequest orderRequest = createOrderRequest(product.getId(), option.getId());

            // when: 대기열 없이 바로 주문
            ResponseEntity<ApiResponse<OrderV1Dto.OrderDetailResponse>> response = testRestTemplate.exchange(
                    ENDPOINT_ORDERS,
                    HttpMethod.POST,
                    new HttpEntity<>(orderRequest, headers),
                    new ParameterizedTypeReference<>() {});

            // then: 기존 흐름대로 주문 성공
            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(response.getBody()).isNotNull(),
                    () -> assertThat(response.getBody().data().id()).isNotNull());
        }

        @Test
        @DisplayName("flag가 없어도(row 미존재) 토큰 없이 주문에 성공한다")
        void order_without_flag_row_success() {
            // given: feature_flag 테이블에 QUEUE_ENABLED row 없음
            Member member = saveMember("testuser1");
            Brand brand = saveActiveBrand();
            Product product = saveProduct(brand);
            ProductOption option = saveProductOption(product.getId());
            pointJpaRepository.save(Point.create(member.getId(), 50000));

            HttpHeaders headers = memberAuthHeaders("testuser1");
            OrderV1Dto.CreateOrderRequest orderRequest = createOrderRequest(product.getId(), option.getId());

            // when
            ResponseEntity<ApiResponse<OrderV1Dto.OrderDetailResponse>> response = testRestTemplate.exchange(
                    ENDPOINT_ORDERS,
                    HttpMethod.POST,
                    new HttpEntity<>(orderRequest, headers),
                    new ParameterizedTypeReference<>() {});

            // then: flag 없음 → false → 대기열 bypass → 주문 성공
            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(response.getBody()).isNotNull(),
                    () -> assertThat(response.getBody().data().id()).isNotNull());
        }
    }

    @Nested
    @DisplayName("대기열 API")
    class QueueApi {

        @Test
        @DisplayName("대기열 비활성 상태에서 진입 시도하면 실패한다")
        void enter_when_flag_off_fails() {
            // given
            saveQueueFlag(false);
            saveMember("testuser1");
            HttpHeaders headers = memberAuthHeaders("testuser1");

            // when
            ResponseEntity<ApiResponse<QueueV1Dto.EnterResponse>> response = testRestTemplate.exchange(
                    ENDPOINT_QUEUE_ENTER,
                    HttpMethod.POST,
                    new HttpEntity<>(headers),
                    new ParameterizedTypeReference<>() {});

            // then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @Test
        @DisplayName("동일 유저가 두 번 진입해도 동일한 순번을 반환한다 (멱등성)")
        void enter_twice_returns_same_position() {
            // given
            saveQueueFlag(true);
            saveMember("testuser1");
            HttpHeaders headers = memberAuthHeaders("testuser1");

            // when
            ResponseEntity<ApiResponse<QueueV1Dto.EnterResponse>> first = testRestTemplate.exchange(
                    ENDPOINT_QUEUE_ENTER,
                    HttpMethod.POST,
                    new HttpEntity<>(headers),
                    new ParameterizedTypeReference<>() {});
            ResponseEntity<ApiResponse<QueueV1Dto.EnterResponse>> second = testRestTemplate.exchange(
                    ENDPOINT_QUEUE_ENTER,
                    HttpMethod.POST,
                    new HttpEntity<>(headers),
                    new ParameterizedTypeReference<>() {});

            // then
            assertAll(
                    () -> assertThat(first.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(second.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(first.getBody().data().position())
                            .isEqualTo(second.getBody().data().position()));
        }

        @Test
        @DisplayName("인증 없이 대기열 진입 시 401을 반환한다")
        void enter_without_auth_returns_401() {
            // given
            saveQueueFlag(true);
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            // when
            ResponseEntity<ApiResponse<QueueV1Dto.EnterResponse>> response = testRestTemplate.exchange(
                    ENDPOINT_QUEUE_ENTER,
                    HttpMethod.POST,
                    new HttpEntity<>(headers),
                    new ParameterizedTypeReference<>() {});

            // then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }
    }

    @Nested
    @DisplayName("토큰 1회성 사용 검증")
    class TokenOneTimeUse {

        @Test
        @DisplayName("주문 성공 후 동일 토큰으로 재주문하면 차단된다")
        void order_success_then_reorder_blocked() {
            // given
            saveQueueFlag(true);
            Member member = saveMember("testuser1");
            Brand brand = saveActiveBrand();
            Product product = saveProduct(brand);
            ProductOption option = saveProductOption(product.getId());
            pointJpaRepository.save(Point.create(member.getId(), 50000));

            HttpHeaders headers = memberAuthHeaders("testuser1");

            // when: 1. 대기열 진입
            testRestTemplate.exchange(
                    ENDPOINT_QUEUE_ENTER, HttpMethod.POST,
                    new HttpEntity<>(headers), new ParameterizedTypeReference<ApiResponse<QueueV1Dto.EnterResponse>>() {});

            // when: 2. 토큰 수동 발급
            queueTokenService.issueToken(member.getId());

            // when: 3. 첫 번째 주문 — 성공
            OrderV1Dto.CreateOrderRequest orderRequest = createOrderRequest(product.getId(), option.getId());
            ResponseEntity<ApiResponse<OrderV1Dto.OrderDetailResponse>> firstOrder = testRestTemplate.exchange(
                    ENDPOINT_ORDERS, HttpMethod.POST,
                    new HttpEntity<>(orderRequest, headers), new ParameterizedTypeReference<>() {});

            assertThat(firstOrder.getStatusCode()).isEqualTo(HttpStatus.OK);

            // when: 4. 동일 유저가 재주문 시도 — 토큰이 삭제되어 차단
            ResponseEntity<ApiResponse<OrderV1Dto.OrderDetailResponse>> secondOrder = testRestTemplate.exchange(
                    ENDPOINT_ORDERS, HttpMethod.POST,
                    new HttpEntity<>(orderRequest, headers), new ParameterizedTypeReference<>() {});

            // then: afterCompletion에서 토큰이 삭제되었으므로 차단
            assertThat(secondOrder.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }
    }

    @Nested
    @DisplayName("대기 중 순번 조회")
    class PositionWhileWaiting {

        @Test
        @DisplayName("토큰 발급 전 순번 조회 시 position > 0, token null, pollIntervalSeconds > 0을 반환한다")
        void position_before_token_issued() {
            // given
            saveQueueFlag(true);
            saveMember("testuser1");
            HttpHeaders headers = memberAuthHeaders("testuser1");

            // when: 1. 대기열 진입
            ResponseEntity<ApiResponse<QueueV1Dto.EnterResponse>> enterResponse = testRestTemplate.exchange(
                    ENDPOINT_QUEUE_ENTER, HttpMethod.POST,
                    new HttpEntity<>(headers), new ParameterizedTypeReference<>() {});

            assertThat(enterResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

            // when: 2. 토큰 발급 없이 바로 순번 조회
            ResponseEntity<ApiResponse<QueueV1Dto.PositionResponse>> positionResponse = testRestTemplate.exchange(
                    ENDPOINT_QUEUE_POSITION, HttpMethod.GET,
                    new HttpEntity<>(headers), new ParameterizedTypeReference<>() {});

            // then: 대기 중 상태 — position > 0, token null, pollIntervalSeconds > 0
            assertAll(
                    () -> assertThat(positionResponse.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(positionResponse.getBody()).isNotNull(),
                    () -> assertThat(positionResponse.getBody().data().position()).isGreaterThan(0),
                    () -> assertThat(positionResponse.getBody().data().token()).isNull(),
                    () -> assertThat(positionResponse.getBody().data().estimatedWaitSeconds()).isGreaterThanOrEqualTo(0),
                    () -> assertThat(positionResponse.getBody().data().pollIntervalSeconds()).isGreaterThan(0));
        }
    }

    @Nested
    @DisplayName("다수 유저 동시 진입")
    class ConcurrentEnter {

        @Test
        @DisplayName("5명이 동시에 대기열에 진입해도 모두 고유한 순번을 받는다")
        void concurrent_enter_unique_positions() throws InterruptedException {
            // given
            saveQueueFlag(true);
            int userCount = 5;
            List<Member> members = new ArrayList<>();
            for (int i = 1; i <= userCount; i++) {
                members.add(saveMember("concurrent" + i));
            }

            ExecutorService executor = Executors.newFixedThreadPool(userCount);
            CountDownLatch latch = new CountDownLatch(userCount);
            List<Long> positions = new ArrayList<>();
            AtomicInteger successCount = new AtomicInteger(0);

            // when: 동시에 대기열 진입
            for (int i = 0; i < userCount; i++) {
                String loginId = "concurrent" + (i + 1);
                executor.submit(() -> {
                    try {
                        HttpHeaders headers = memberAuthHeaders(loginId);
                        ResponseEntity<ApiResponse<QueueV1Dto.EnterResponse>> response = testRestTemplate.exchange(
                                ENDPOINT_QUEUE_ENTER, HttpMethod.POST,
                                new HttpEntity<>(headers), new ParameterizedTypeReference<>() {});

                        if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                            synchronized (positions) {
                                positions.add(response.getBody().data().position());
                            }
                            successCount.incrementAndGet();
                        }
                    } finally {
                        latch.countDown();
                    }
                });
            }
            latch.await();
            executor.shutdown();

            // then: 전원 성공, 모두 고유한 순번
            assertAll(
                    () -> assertThat(successCount.get()).isEqualTo(userCount),
                    () -> assertThat(positions).hasSize(userCount),
                    () -> assertThat(positions).doesNotHaveDuplicates());
        }
    }
}
