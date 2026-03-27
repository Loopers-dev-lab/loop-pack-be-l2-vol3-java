package com.loopers.application.coupon;

import com.loopers.application.coupon.command.UseCouponCommand;
import com.loopers.domain.brand.Brand;
import com.loopers.domain.brand.BrandRepository;
import com.loopers.domain.brand.vo.BrandName;
import com.loopers.domain.category.Category;
import com.loopers.domain.category.CategoryRepository;
import com.loopers.domain.coupon.CouponStatus;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductRepository;
import com.loopers.domain.coupon.IssuedCoupon;
import com.loopers.domain.coupon.IssuedCouponRepository;
import com.loopers.infrastructure.coupon.CouponEntity;
import com.loopers.infrastructure.coupon.CouponJpaRepository;
import com.loopers.domain.coupon.CouponType;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.member.MemberDto;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
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
import java.util.UUID;
import java.time.LocalDateTime;
import java.util.concurrent.ConcurrentHashMap;
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
    private static final String TEST_LOGIN_ID = "couponuseuser1";
    private static final String TEST_PASSWORD = "Test1234!@";

    @Autowired
    private TestRestTemplate testRestTemplate;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @Autowired
    private BrandRepository brandRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private CouponJpaRepository couponJpaRepository;

    @Autowired
    private IssuedCouponRepository issuedCouponRepository;

    @Autowired
    private CouponApplicationService couponApplicationService;

    private UUID couponId;
    private UUID productId;

    @BeforeEach
    void setUp() {
        MemberDto.RegisterRequest registerRequest = new MemberDto.RegisterRequest(
                TEST_LOGIN_ID,
                TEST_PASSWORD,
                "쿠폰유저",
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

        UUID categoryId = categoryRepository.save(new Category("쿠폰동시성카테고리")).id();
        UUID brandId = brandRepository.save(new Brand(new BrandName("쿠폰동시성브랜드"), "desc", "img")).id();
        productId = productRepository.save(new Product("쿠폰동시성상품", 10000, 100, "desc", categoryId, brandId)).id();

        CouponEntity couponEntity = couponJpaRepository.saveAndFlush(
                new CouponEntity("동시성쿠폰", CouponType.FIXED, 1000, 0, 100, 100, LocalDateTime.now().plusDays(1))
        );
        couponId = couponEntity.getId();
        assertThat(couponId).isNotNull();

        issuedCouponRepository.save(new IssuedCoupon(
                TEST_LOGIN_ID,
                couponId,
                CouponStatus.AVAILABLE,
                LocalDateTime.now(),
                LocalDateTime.now().plusDays(1),
                null
        ));
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @Test
    @DisplayName("동일 couponId로 동시 주문 시 1건만 성공해야 한다")
    void sameCouponConcurrentOrder() throws Exception {
        int threadCount = 8;

        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);
        Map<Integer, AtomicInteger> statusCounts = new ConcurrentHashMap<>();

        List<Runnable> tasks = java.util.stream.IntStream.range(0, threadCount)
                .mapToObj(i -> (Runnable) () -> {
                    try {
                        startLatch.await();
                        Map<String, Object> body = Map.of(
                                "items", List.of(Map.of("productId", productId, "quantity", 1)),
                                "couponId", couponId,
                                "pointAmount", 0,
                                "cardType", "SAMSUNG",
                                "cardNo", "1234-5678-1234-5678"
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
                        statusCounts.computeIfAbsent(response.getStatusCode().value(), ignored -> new AtomicInteger(0))
                                .incrementAndGet();
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

        assertThat(successCount.get())
                .withFailMessage("statusCounts=%s", statusCounts)
                .isEqualTo(1);
        assertThat(failCount.get()).isEqualTo(threadCount - 1);
    }

    @Test
    @DisplayName("동일 couponId 사용 취소 동시 요청 시 1건만 성공해야 한다")
    void sameCouponConcurrentCancelUse() throws Exception {
        couponApplicationService.use(new UseCouponCommand(couponId, TEST_LOGIN_ID, 10000));

        int threadCount = 8;
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger conflictCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            executorService.execute(() -> {
                try {
                    startLatch.await();
                    couponApplicationService.cancelUse(couponId, TEST_LOGIN_ID);
                    successCount.incrementAndGet();
                } catch (CoreException e) {
                    if (e.getErrorType() == ErrorType.CONFLICT) {
                        conflictCount.incrementAndGet();
                    } else {
                        failureCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    failureCount.incrementAndGet();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        doneLatch.await();
        executorService.shutdown();

        assertThat(successCount.get()).isEqualTo(1);
        assertThat(conflictCount.get()).isEqualTo(threadCount - 1);
        assertThat(failureCount.get()).isZero();
    }

    private HttpHeaders authHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HEADER_LOGIN_ID, TEST_LOGIN_ID);
        headers.set(HEADER_LOGIN_PW, TEST_PASSWORD);
        return headers;
    }
}
