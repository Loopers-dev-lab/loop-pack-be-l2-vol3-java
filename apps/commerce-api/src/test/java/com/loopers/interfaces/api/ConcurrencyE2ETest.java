package com.loopers.interfaces.api;

import com.loopers.domain.brand.Brand;
import com.loopers.domain.coupon.Coupon;
import com.loopers.domain.coupon.IssuedCoupon;
import com.loopers.domain.product.Product;
import com.loopers.domain.user.User;
import com.loopers.domain.user.UserFixture;
import com.loopers.infrastructure.brand.BrandJpaRepository;
import com.loopers.infrastructure.coupon.CouponJpaRepository;
import com.loopers.infrastructure.coupon.IssuedCouponJpaRepository;
import com.loopers.infrastructure.product.ProductJpaRepository;
import com.loopers.infrastructure.user.UserJpaRepository;
import com.loopers.interfaces.api.order.OrderV1Dto;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
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
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ConcurrencyE2ETest {

    private static final String ORDERS_ENDPOINT = "/api/v1/orders";
    private static final String RAW_PASSWORD = "TestPass1!";

    private final TestRestTemplate testRestTemplate;
    private final UserJpaRepository userJpaRepository;
    private final BrandJpaRepository brandJpaRepository;
    private final ProductJpaRepository productJpaRepository;
    private final CouponJpaRepository couponJpaRepository;
    private final IssuedCouponJpaRepository issuedCouponJpaRepository;
    private final DatabaseCleanUp databaseCleanUp;
    private final BCryptPasswordEncoder bCryptPasswordEncoder = new BCryptPasswordEncoder();

    @Autowired
    public ConcurrencyE2ETest(
            TestRestTemplate testRestTemplate,
            UserJpaRepository userJpaRepository,
            BrandJpaRepository brandJpaRepository,
            ProductJpaRepository productJpaRepository,
            CouponJpaRepository couponJpaRepository,
            IssuedCouponJpaRepository issuedCouponJpaRepository,
            DatabaseCleanUp databaseCleanUp
    ) {
        this.testRestTemplate = testRestTemplate;
        this.userJpaRepository = userJpaRepository;
        this.brandJpaRepository = brandJpaRepository;
        this.productJpaRepository = productJpaRepository;
        this.couponJpaRepository = couponJpaRepository;
        this.issuedCouponJpaRepository = issuedCouponJpaRepository;
        this.databaseCleanUp = databaseCleanUp;
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    private HttpHeaders headersFor(String loginId) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Loopers-LoginId", loginId);
        headers.set("X-Loopers-LoginPw", RAW_PASSWORD);
        return headers;
    }

    @DisplayName("재고가 N개인 상품에 M명(M>N)이 동시 주문하면, 정확히 N건만 성공하고 최종 재고는 0이다.")
    @Test
    void 재고_동시_차감_테스트() throws InterruptedException {
        // arrange
        int stock = 5;
        int threadCount = 10;

        String encodedPassword = bCryptPasswordEncoder.encode(RAW_PASSWORD);
        Brand brand = brandJpaRepository.save(Brand.create("나이키", "스포츠"));
        Product product = productJpaRepository.save(Product.create(brand.getId(), "에어맥스", null, 10000, stock));

        List<User> users = new ArrayList<>();
        for (int i = 0; i < threadCount; i++) {
            users.add(userJpaRepository.save(
                    UserFixture.builder().loginId("stockUser" + i).password(encodedPassword).build()));
        }

        OrderV1Dto.CreateRequest request = new OrderV1Dto.CreateRequest(
                List.of(new OrderV1Dto.OrderItemRequest(product.getId(), 1)), null);

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        ConcurrentLinkedQueue<String> failErrorCodes = new ConcurrentLinkedQueue<>();

        for (int i = 0; i < threadCount; i++) {
            final User user = users.get(i);
            executor.submit(() -> {
                try {
                    startLatch.await();
                    HttpEntity<OrderV1Dto.CreateRequest> entity = new HttpEntity<>(request, headersFor(user.getLoginId()));
                    ResponseEntity<ApiResponse<OrderV1Dto.OrderResponse>> response =
                            testRestTemplate.exchange(ORDERS_ENDPOINT, HttpMethod.POST, entity, new ParameterizedTypeReference<>() {});
                    if (response.getStatusCode() == HttpStatus.CREATED) {
                        successCount.incrementAndGet();
                    } else {
                        failErrorCodes.add(response.getBody().meta().errorCode());
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

        // assert
        Product finalProduct = productJpaRepository.findById(product.getId()).orElseThrow();
        assertThat(completed).isTrue();
        assertThat(successCount.get()).isEqualTo(stock);
        assertThat(failErrorCodes).hasSize(threadCount - stock);
        assertThat(failErrorCodes).containsOnly("INSUFFICIENT_STOCK");
        assertThat(finalProduct.getStockQuantity()).isEqualTo(0);
    }

    @DisplayName("동일 쿠폰으로 N번 동시 주문하면, 정확히 1건만 성공하고 쿠폰은 사용 처리된다.")
    @Test
    void 쿠폰_동시_사용_테스트() throws InterruptedException {
        // arrange
        int threadCount = 10;

        String encodedPassword = bCryptPasswordEncoder.encode(RAW_PASSWORD);
        User user = userJpaRepository.save(UserFixture.builder().loginId("couponConcUser").password(encodedPassword).build());
        Brand brand = brandJpaRepository.save(Brand.create("나이키", "스포츠"));
        Product product = productJpaRepository.save(Product.create(brand.getId(), "에어맥스", null, 10000, 100));

        Coupon coupon = couponJpaRepository.save(
                Coupon.create("동시성 테스트 쿠폰", Coupon.DiscountType.FIXED, 1000L, 1000L, LocalDateTime.now().plusDays(30)));
        IssuedCoupon issuedCoupon = issuedCouponJpaRepository.save(
                IssuedCoupon.create(user.getId(), coupon.getId(), LocalDateTime.now().plusDays(30)));

        OrderV1Dto.CreateRequest request = new OrderV1Dto.CreateRequest(
                List.of(new OrderV1Dto.OrderItemRequest(product.getId(), 1)), issuedCoupon.getId());
        HttpEntity<OrderV1Dto.CreateRequest> entity = new HttpEntity<>(request, headersFor(user.getLoginId()));

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        ConcurrentLinkedQueue<String> failErrorCodes = new ConcurrentLinkedQueue<>();

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    ResponseEntity<ApiResponse<OrderV1Dto.OrderResponse>> response =
                            testRestTemplate.exchange(ORDERS_ENDPOINT, HttpMethod.POST, entity, new ParameterizedTypeReference<>() {});
                    if (response.getStatusCode() == HttpStatus.CREATED) {
                        successCount.incrementAndGet();
                    } else {
                        failErrorCodes.add(response.getBody().meta().errorCode());
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

        // assert
        IssuedCoupon usedCoupon = issuedCouponJpaRepository.findById(issuedCoupon.getId()).orElseThrow();
        assertThat(completed).isTrue();
        assertThat(successCount.get()).isEqualTo(1);
        assertThat(failErrorCodes).hasSize(threadCount - 1);
        assertThat(failErrorCodes).containsOnly("COUPON_ALREADY_USED");
        assertThat(usedCoupon.getUsedAt()).isNotNull();
    }

}
