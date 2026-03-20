package com.loopers.domain.like;

import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductRepository;
import com.loopers.domain.product.ProductService;
import com.loopers.domain.product.StockService;
import com.loopers.domain.user.UserModel;
import com.loopers.domain.user.UserRegisterCommand;
import com.loopers.domain.user.UserService;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@DisplayName("좋아요 동시성 테스트")
class LikeConcurrencyTest {

    @Autowired
    LikeService likeService;

    @Autowired
    LikeRepository likeRepository;

    @Autowired
    ProductService productService;

    @Autowired
    ProductRepository productRepository;

    @Autowired
    StockService stockService;

    @Autowired
    UserService userService;

    @Autowired
    DatabaseCleanUp databaseCleanUp;

    private Long productId;

    @BeforeEach
    void setUp() {
        ProductModel product = productService.createProduct("테스트상품", 1L, BigDecimal.valueOf(10000), "설명");
        productId = product.getProductId();
        stockService.createStock(productId, 100);
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @Test
    @DisplayName("20명이 동시에 같은 상품 좋아요 시 like 수 == 20, likeCount == 20")
    void concurrentAddLike_DifferentUsers_ShouldAllSucceed() throws InterruptedException {
        int threadCount = 20;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);

        // 사용자 20명 생성
        Long[] userIds = new Long[threadCount];
        for (int i = 0; i < threadCount; i++) {
            UserModel user = userService.register(new UserRegisterCommand(
                    "likeuser" + i, "Test1234!@#", "유저" + i,
                    "19900101", "user" + i + "@test.com", "서울"));
            userIds[i] = user.getUserId();
        }

        for (int i = 0; i < threadCount; i++) {
            final int index = i;
            executor.submit(() -> {
                try {
                    startLatch.await();
                    likeService.addLike(userIds[index], productId);
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    // 예외 발생 시 실패 카운트
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        doneLatch.await();
        executor.shutdown();

        assertThat(successCount.get()).isEqualTo(20);

        long likeCount = likeRepository.countByProductId(productId);
        assertThat(likeCount).isEqualTo(20);

        ProductModel product = productRepository.findById(productId).get();
        assertThat(product.getLikeCount()).isEqualTo(20);
    }

    @Test
    @DisplayName("같은 유저가 5번 동시 좋아요 시 like 수 == 1, likeCount == 1 (멱등)")
    void concurrentAddLike_SameUser_ShouldBeIdempotent() throws InterruptedException {
        int threadCount = 5;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);

        UserModel user = userService.register(new UserRegisterCommand(
                "sameuser", "Test1234!@#", "같은유저",
                "19900101", "same@test.com", "서울"));
        Long userId = user.getUserId();

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    likeService.addLike(userId, productId);
                } catch (Exception e) {
                    // 멱등 처리로 예외는 무시
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        doneLatch.await();
        executor.shutdown();

        long likeCount = likeRepository.countByProductId(productId);
        assertThat(likeCount).isEqualTo(1);

        ProductModel product = productRepository.findById(productId).get();
        assertThat(product.getLikeCount()).isEqualTo(1);
    }
}
