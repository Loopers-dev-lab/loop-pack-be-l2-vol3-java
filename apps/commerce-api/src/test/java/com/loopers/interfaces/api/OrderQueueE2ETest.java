package com.loopers.interfaces.api;

import com.loopers.domain.brand.Brand;
import com.loopers.domain.product.Product;
import com.loopers.domain.user.User;
import com.loopers.domain.user.UserFixture;
import com.loopers.infrastructure.brand.BrandJpaRepository;
import com.loopers.infrastructure.product.ProductJpaRepository;
import com.loopers.infrastructure.user.UserJpaRepository;
import com.loopers.interfaces.api.order.OrderV1Dto;
import com.loopers.interfaces.api.queue.OrderQueueV1Dto;
import com.loopers.utils.DatabaseCleanUp;
import com.loopers.utils.RedisCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class OrderQueueE2ETest {

    private static final String QUEUE_ENTER_ENDPOINT = "/api/v1/queue/enter";
    private static final String QUEUE_POSITION_ENDPOINT = "/api/v1/queue/position";
    private static final String ORDERS_ENDPOINT = "/api/v1/orders";
    private static final String ORDER_QUEUE_ENABLED_KEY = "order:queue:enabled";
    private static final String RAW_PASSWORD = "TestPass1!";

    private final TestRestTemplate testRestTemplate;
    private final UserJpaRepository userJpaRepository;
    private final BrandJpaRepository brandJpaRepository;
    private final ProductJpaRepository productJpaRepository;
    private final DatabaseCleanUp databaseCleanUp;
    private final RedisCleanUp redisCleanUp;
    private final RedisTemplate<String, String> redisTemplate;
    private final BCryptPasswordEncoder bCryptPasswordEncoder = new BCryptPasswordEncoder();

    private User savedUser;
    private Product savedProduct;

    @Autowired
    public OrderQueueE2ETest(
            TestRestTemplate testRestTemplate,
            UserJpaRepository userJpaRepository,
            BrandJpaRepository brandJpaRepository,
            ProductJpaRepository productJpaRepository,
            DatabaseCleanUp databaseCleanUp,
            RedisCleanUp redisCleanUp,
            RedisTemplate<String, String> redisTemplate
    ) {
        this.testRestTemplate = testRestTemplate;
        this.userJpaRepository = userJpaRepository;
        this.brandJpaRepository = brandJpaRepository;
        this.productJpaRepository = productJpaRepository;
        this.databaseCleanUp = databaseCleanUp;
        this.redisCleanUp = redisCleanUp;
        this.redisTemplate = redisTemplate;
    }

    @BeforeEach
    void setUp() {
        String encodedPassword = bCryptPasswordEncoder.encode(RAW_PASSWORD);
        savedUser = userJpaRepository.save(
                UserFixture.builder().loginId("queueUser").password(encodedPassword).build()
        );
        Brand brand = brandJpaRepository.save(Brand.create("나이키", "스포츠"));
        savedProduct = productJpaRepository.save(Product.create(brand.getId(), "에어맥스", null, 150000, 100));
    }

    @AfterEach
    void tearDown() {
        try {
            databaseCleanUp.truncateAllTables();
        } finally {
            redisCleanUp.truncateAll();
        }
    }

    private HttpHeaders userHeaders(User user) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Loopers-LoginId", user.getLoginId());
        headers.set("X-Loopers-LoginPw", RAW_PASSWORD);
        return headers;
    }

    private HttpHeaders userHeadersWithToken(User user, String entryToken) {
        HttpHeaders headers = userHeaders(user);
        headers.set("X-Entry-Token", entryToken);
        return headers;
    }

    private void enableQueue() {
        redisTemplate.opsForValue().set(ORDER_QUEUE_ENABLED_KEY, "true");
    }

    @DisplayName("대기열 진입 및 순번 조회")
    @Nested
    class QueueEntryAndPosition {

        @DisplayName("대기열에 진입하면 순번과 대기 정보가 반환된다.")
        @Test
        void returnsPositionInfo_whenEnterQueue() {
            // act
            HttpEntity<Void> entity = new HttpEntity<>(userHeaders(savedUser));
            ResponseEntity<ApiResponse<OrderQueueV1Dto.EnterResponse>> response =
                    testRestTemplate.exchange(QUEUE_ENTER_ENDPOINT, HttpMethod.POST, entity, new ParameterizedTypeReference<>() {});

            // assert
            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(response.getBody().data().position()).isEqualTo(1),
                    () -> assertThat(response.getBody().data().totalWaiting()).isEqualTo(1),
                    () -> assertThat(response.getBody().data().isNew()).isTrue()
            );
        }

        @DisplayName("같은 유저가 다시 진입하면 isNew가 false이다.")
        @Test
        void returnsNotNew_whenDuplicateEntry() {
            // arrange
            HttpEntity<Void> entity = new HttpEntity<>(userHeaders(savedUser));
            testRestTemplate.exchange(QUEUE_ENTER_ENDPOINT, HttpMethod.POST, entity, new ParameterizedTypeReference<ApiResponse<OrderQueueV1Dto.EnterResponse>>() {});

            // act
            ResponseEntity<ApiResponse<OrderQueueV1Dto.EnterResponse>> response =
                    testRestTemplate.exchange(QUEUE_ENTER_ENDPOINT, HttpMethod.POST, entity, new ParameterizedTypeReference<>() {});

            // assert
            assertThat(response.getBody().data().isNew()).isFalse();
        }

        @DisplayName("순번 조회 시 대기 중인 유저의 순번이 반환된다.")
        @Test
        void returnsWaitingStatus_whenPolling() {
            // arrange: 대기열 진입
            HttpEntity<Void> entity = new HttpEntity<>(userHeaders(savedUser));
            testRestTemplate.exchange(QUEUE_ENTER_ENDPOINT, HttpMethod.POST, entity, new ParameterizedTypeReference<ApiResponse<OrderQueueV1Dto.EnterResponse>>() {});

            // act: 순번 조회
            ResponseEntity<ApiResponse<OrderQueueV1Dto.PositionResponse>> response =
                    testRestTemplate.exchange(QUEUE_POSITION_ENDPOINT, HttpMethod.GET, entity, new ParameterizedTypeReference<>() {});

            // assert
            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(response.getBody().data().status()).isEqualTo("WAITING"),
                    () -> assertThat(response.getBody().data().position()).isEqualTo(1)
            );
        }

        @DisplayName("대기열에 없는 유저가 순번 조회하면 NOT_IN_QUEUE가 반환된다.")
        @Test
        void returnsNotInQueue_whenNotEnqueued() {
            // act
            HttpEntity<Void> entity = new HttpEntity<>(userHeaders(savedUser));
            ResponseEntity<ApiResponse<OrderQueueV1Dto.PositionResponse>> response =
                    testRestTemplate.exchange(QUEUE_POSITION_ENDPOINT, HttpMethod.GET, entity, new ParameterizedTypeReference<>() {});

            // assert
            assertThat(response.getBody().data().status()).isEqualTo("NOT_IN_QUEUE");
        }
    }

    @DisplayName("동시 대기열 진입")
    @Nested
    class ConcurrentEntry {

        @DisplayName("여러 유저가 동시에 진입해도 순번이 정확히 부여된다.")
        @Test
        void assignsCorrectPositions_whenConcurrentEntry() throws InterruptedException {
            // arrange
            int threadCount = 20;
            String encodedPassword = bCryptPasswordEncoder.encode(RAW_PASSWORD);
            List<User> users = new ArrayList<>();
            for (int i = 0; i < threadCount; i++) {
                users.add(userJpaRepository.save(
                        UserFixture.builder().loginId("concurrent" + i).password(encodedPassword).build()));
            }

            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch doneLatch = new CountDownLatch(threadCount);
            AtomicInteger successCount = new AtomicInteger(0);

            for (int i = 0; i < threadCount; i++) {
                final User user = users.get(i);
                executor.submit(() -> {
                    try {
                        startLatch.await();
                        HttpEntity<Void> entity = new HttpEntity<>(userHeaders(user));
                        ResponseEntity<ApiResponse<OrderQueueV1Dto.EnterResponse>> response =
                                testRestTemplate.exchange(QUEUE_ENTER_ENDPOINT, HttpMethod.POST, entity, new ParameterizedTypeReference<>() {});
                        if (response.getStatusCode() == HttpStatus.OK && response.getBody().data().isNew()) {
                            successCount.incrementAndGet();
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        doneLatch.countDown();
                    }
                });
            }

            // act
            startLatch.countDown();
            boolean completed = doneLatch.await(30, TimeUnit.SECONDS);
            executor.shutdown();

            // assert: 20명 전부 성공 + 대기열에 정확히 20명
            assertAll(
                    () -> assertThat(completed).isTrue(),
                    () -> assertThat(successCount.get()).isEqualTo(threadCount)
            );

            // 순번 조회로 전체 대기 인원 확인
            HttpEntity<Void> entity = new HttpEntity<>(userHeaders(users.get(0)));
            ResponseEntity<ApiResponse<OrderQueueV1Dto.PositionResponse>> positionResponse =
                    testRestTemplate.exchange(QUEUE_POSITION_ENDPOINT, HttpMethod.GET, entity, new ParameterizedTypeReference<>() {});
            assertThat(positionResponse.getBody().data().totalWaiting()).isEqualTo(threadCount);
        }
    }

    @DisplayName("토큰 발급 및 주문 흐름 (대기열 ON)")
    @Nested
    class TokenIssuanceAndOrder {

        @BeforeEach
        void setUp() {
            enableQueue();
        }

        @DisplayName("대기열 진입 → 스케줄러 토큰 발급 → 토큰으로 주문 성공")
        @Test
        void fullFlow_enterQueue_getToken_placeOrder() throws InterruptedException {
            // arrange: 대기열 진입
            HttpEntity<Void> enterEntity = new HttpEntity<>(userHeaders(savedUser));
            testRestTemplate.exchange(QUEUE_ENTER_ENDPOINT, HttpMethod.POST, enterEntity, new ParameterizedTypeReference<ApiResponse<OrderQueueV1Dto.EnterResponse>>() {});

            // act: 스케줄러가 토큰 발급할 때까지 polling (최대 3초)
            String token = null;
            for (int i = 0; i < 30; i++) {
                Thread.sleep(100);
                ResponseEntity<ApiResponse<OrderQueueV1Dto.PositionResponse>> positionResponse =
                        testRestTemplate.exchange(QUEUE_POSITION_ENDPOINT, HttpMethod.GET, enterEntity, new ParameterizedTypeReference<>() {});
                if ("TOKEN_ISSUED".equals(positionResponse.getBody().data().status())) {
                    token = positionResponse.getBody().data().token();
                    break;
                }
            }

            // assert: 토큰 발급됨
            assertThat(token).isNotNull();

            // act: 토큰으로 주문
            OrderV1Dto.CreateRequest orderRequest = new OrderV1Dto.CreateRequest(
                    List.of(new OrderV1Dto.OrderItemRequest(savedProduct.getId(), 1)), null);
            HttpEntity<OrderV1Dto.CreateRequest> orderEntity = new HttpEntity<>(orderRequest, userHeadersWithToken(savedUser, token));
            ResponseEntity<ApiResponse<OrderV1Dto.OrderResponse>> orderResponse =
                    testRestTemplate.exchange(ORDERS_ENDPOINT, HttpMethod.POST, orderEntity, new ParameterizedTypeReference<>() {});

            // assert: 주문 성공
            assertThat(orderResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        }

        @DisplayName("토큰 없이 주문하면 거부된다.")
        @Test
        void rejectsOrder_whenNoToken() {
            // arrange
            OrderV1Dto.CreateRequest request = new OrderV1Dto.CreateRequest(
                    List.of(new OrderV1Dto.OrderItemRequest(savedProduct.getId(), 1)), null);
            HttpEntity<OrderV1Dto.CreateRequest> entity = new HttpEntity<>(request, userHeaders(savedUser));

            // act
            ResponseEntity<ApiResponse<Object>> response =
                    testRestTemplate.exchange(ORDERS_ENDPOINT, HttpMethod.POST, entity, new ParameterizedTypeReference<>() {});

            // assert
            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN),
                    () -> assertThat(response.getBody().meta().errorCode()).isEqualTo("ENTRY_TOKEN_REQUIRED")
            );
        }

        @DisplayName("잘못된 토큰으로 주문하면 거부된다.")
        @Test
        void rejectsOrder_whenInvalidToken() {
            // arrange
            OrderV1Dto.CreateRequest request = new OrderV1Dto.CreateRequest(
                    List.of(new OrderV1Dto.OrderItemRequest(savedProduct.getId(), 1)), null);
            HttpEntity<OrderV1Dto.CreateRequest> entity = new HttpEntity<>(request, userHeadersWithToken(savedUser, "fake-token"));

            // act
            ResponseEntity<ApiResponse<Object>> response =
                    testRestTemplate.exchange(ORDERS_ENDPOINT, HttpMethod.POST, entity, new ParameterizedTypeReference<>() {});

            // assert
            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN),
                    () -> assertThat(response.getBody().meta().errorCode()).isEqualTo("ENTRY_TOKEN_INVALID")
            );
        }
    }

    @DisplayName("대기열 비활성화 상태")
    @Nested
    class QueueDisabled {

        @DisplayName("토큰 없이도 주문이 성공한다.")
        @Test
        void allowsOrder_withoutToken() {
            // arrange: 대기열 OFF (기본 상태)
            OrderV1Dto.CreateRequest request = new OrderV1Dto.CreateRequest(
                    List.of(new OrderV1Dto.OrderItemRequest(savedProduct.getId(), 1)), null);
            HttpEntity<OrderV1Dto.CreateRequest> entity = new HttpEntity<>(request, userHeaders(savedUser));

            // act
            ResponseEntity<ApiResponse<OrderV1Dto.OrderResponse>> response =
                    testRestTemplate.exchange(ORDERS_ENDPOINT, HttpMethod.POST, entity, new ParameterizedTypeReference<>() {});

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        }

        @DisplayName("토큰을 보내면 거부된다.")
        @Test
        void rejectsOrder_whenTokenProvided() {
            // arrange: 대기열 OFF인데 토큰을 보냄
            OrderV1Dto.CreateRequest request = new OrderV1Dto.CreateRequest(
                    List.of(new OrderV1Dto.OrderItemRequest(savedProduct.getId(), 1)), null);
            HttpEntity<OrderV1Dto.CreateRequest> entity = new HttpEntity<>(request, userHeadersWithToken(savedUser, "unexpected-token"));

            // act
            ResponseEntity<ApiResponse<Object>> response =
                    testRestTemplate.exchange(ORDERS_ENDPOINT, HttpMethod.POST, entity, new ParameterizedTypeReference<>() {});

            // assert
            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST),
                    () -> assertThat(response.getBody().meta().errorCode()).isEqualTo("ENTRY_TOKEN_NOT_ACCEPTED")
            );
        }
    }

    @DisplayName("스케줄러 점진적 토큰 발급")
    @Nested
    class GradualTokenIssuance {

        @DisplayName("배치 크기보다 많은 대기자가 있으면 점진적으로 토큰이 발급된다.")
        @Test
        void issuesTokensGradually_whenMoreThanBatchSize() throws InterruptedException {
            // arrange: 20명 대기열 진입
            int userCount = 20;
            String encodedPassword = bCryptPasswordEncoder.encode(RAW_PASSWORD);
            List<User> users = new ArrayList<>();
            for (int i = 0; i < userCount; i++) {
                User user = userJpaRepository.save(UserFixture.builder().loginId("gradual" + i).password(encodedPassword).build());
                users.add(user);

                HttpEntity<Void> entity = new HttpEntity<>(userHeaders(user));
                testRestTemplate.exchange(QUEUE_ENTER_ENDPOINT, HttpMethod.POST, entity, new ParameterizedTypeReference<ApiResponse<OrderQueueV1Dto.EnterResponse>>() {});
            }

            // act: 스케줄러가 토큰 발급할 시간 대기 (200ms → 첫 배치 14명 발급)
            Thread.sleep(300);

            // assert: polling으로 토큰 발급 상태 확인
            int tokenIssuedCount = 0;
            int waitingCount = 0;
            for (User user : users) {
                HttpEntity<Void> entity = new HttpEntity<>(userHeaders(user));
                ResponseEntity<ApiResponse<OrderQueueV1Dto.PositionResponse>> response =
                        testRestTemplate.exchange(QUEUE_POSITION_ENDPOINT, HttpMethod.GET, entity, new ParameterizedTypeReference<>() {});
                String status = response.getBody().data().status();
                if ("TOKEN_ISSUED".equals(status)) {
                    tokenIssuedCount++;
                } else if ("WAITING".equals(status)) {
                    waitingCount++;
                }
            }

            // 14명에게 토큰 발급, 6명은 아직 대기 중
            final int finalTokenIssuedCount = tokenIssuedCount;
            final int finalWaitingCount = waitingCount;
            assertAll(
                    () -> assertThat(finalTokenIssuedCount).isEqualTo(14),
                    () -> assertThat(finalWaitingCount).isEqualTo(6)
            );
        }
    }
}
