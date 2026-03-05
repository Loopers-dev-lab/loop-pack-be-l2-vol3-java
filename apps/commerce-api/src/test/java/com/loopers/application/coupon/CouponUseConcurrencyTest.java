package com.loopers.application.coupon;

import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.member.MemberDto;
import com.loopers.testcontainers.MySqlTestContainersConfig;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.context.ImportTestcontainers;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ImportTestcontainers(MySqlTestContainersConfig.class)
@ActiveProfiles("test")
@DisplayName("Coupon 사용 동시성 테스트")
class CouponUseConcurrencyTest {

    private static final String HEADER_LOGIN_ID = "X-Loopers-LoginId";
    private static final String HEADER_LOGIN_PW = "X-Loopers-LoginPw";
    private static final String TEST_LOGIN_ID = "coupon-use-concurrency-user";
    private static final String TEST_PASSWORD = "Test1234!@";

    @Autowired
    private TestRestTemplate testRestTemplate;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @BeforeEach
    void setUp() {
        MemberDto.RegisterRequest registerRequest = new MemberDto.RegisterRequest(
                TEST_LOGIN_ID,
                TEST_PASSWORD,
                "쿠폰사용동시성",
                "19900101",
                "coupon.use.concurrent@test.com",
                "010-3333-4444"
        );

        testRestTemplate.exchange(
                "/api/v1/members",
                HttpMethod.POST,
                new HttpEntity<>(registerRequest),
                new ParameterizedTypeReference<ApiResponse<Void>>() {}
        );
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @Test
    @DisplayName("동일 couponId로 동시 주문 시 1건만 성공해야 한다")
    void sameCouponConcurrentOrder() throws Exception {
        int threadCount = 8;
        long couponId = 42L;
        long productId = 1L;

        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        List<Runnable> tasks = java.util.stream.IntStream.range(0, threadCount)
                .mapToObj(i -> (Runnable) () -> {
                    try {
                        startLatch.await();
                        Map<String, Object> body = Map.of(
                                "items", List.of(Map.of("productId", productId, "quantity", 1)),
                                "couponId", couponId
                        );

                        ResponseEntity<ApiResponse<Map<String, Object>>> response = testRestTemplate.exchange(
                                "/api/v1/orders",
                                HttpMethod.POST,
                                new HttpEntity<>(body, authHeaders()),
                                new ParameterizedTypeReference<ApiResponse<Map<String, Object>>>() {}
                        );

                        if (response.getStatusCode() == HttpStatus.CREATED) {
                            successCount.incrementAndGet();
                        } else {
                            failCount.incrementAndGet();
                        }
                    } catch (Exception ignored) {
                        failCount.incrementAndGet();
                    } finally {
                        doneLatch.countDown();
                    }
                })
                .toList();

        tasks.forEach(executorService::submit);
        startLatch.countDown();
        doneLatch.await();
        executorService.shutdown();

        assertThat(successCount.get()).isEqualTo(1);
        assertThat(failCount.get()).isEqualTo(threadCount - 1);
    }

    private HttpHeaders authHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HEADER_LOGIN_ID, TEST_LOGIN_ID);
        headers.set(HEADER_LOGIN_PW, TEST_PASSWORD);
        return headers;
    }
}
