package com.loopers.domain.like;

import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductService;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class LikeConcurrencyTest {

    @Autowired
    private ProductLikeService productLikeService;

    @Autowired
    private ProductLikeRepository productLikeRepository;

    // [추가] likeCount 검증을 위해 ProductService 주입
    @Autowired
    private ProductService productService;

    // [추가] 테스트 데이터 정리용
    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    // [추가] Atomic Update 테스트를 위해 실제 상품이 DB에 존재해야 한다
    private Long productId;
    private Long productId2;

    @BeforeEach
    void setUp() {
        ProductModel product = productService.register(1L, "테스트상품", "설명", 10000, 100, null);
        productId = product.getId();

        ProductModel product2 = productService.register(1L, "테스트상품2", "설명", 10000, 100, null);
        productId2 = product2.getId();
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("동일 상품에 여러 명이 동시에 좋아요해도, 좋아요 수가 정상 반영된다")
    @Test
    void concurrentAddLike() throws InterruptedException {
        // given
        int threadCount = 10;

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        // when: 서로 다른 회원 10명이 동시에 같은 상품에 좋아요
        for (int i = 0; i < threadCount; i++) {
            long memberId = i + 1;
            executor.submit(() -> {
                try {
                    productLikeService.addLike(memberId, productId);
                    // [추가] 좋아요 등록 후 likeCount Atomic Update 호출
                    productService.increaseLikeCount(productId);
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    failCount.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();

        // then: 좋아요 수 == 성공 수, 전원 성공
        long likeCount = productLikeRepository.countByProductId(productId);
        assertThat(likeCount).isEqualTo(successCount.get());
        assertThat(successCount.get()).isEqualTo(10);
        assertThat(failCount.get()).isEqualTo(0);

        // [추가] product 테이블의 likeCount도 동일하게 10 — Atomic Update 동시성 검증
        ProductModel product = productService.getById(productId);
        assertThat(product.getLikeCount()).isEqualTo(10);
    }

    @DisplayName("같은 회원이 동시에 좋아요를 두 번 눌러도, 1번만 저장된다")
    @Test
    void concurrentDuplicateLike() throws InterruptedException {
        // given
        Long memberId = 100L;
        int threadCount = 2;

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        // when: 같은 회원이 동시에 2번 좋아요 시도
        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    productLikeService.addLike(memberId, productId2);
                    // [추가] 좋아요 등록 성공 시에만 likeCount 증가
                    productService.increaseLikeCount(productId2);
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    failCount.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();

        // then: UniqueConstraint 덕분에 1번만 저장, 1번은 실패
        long likeCount = productLikeRepository.countByProductId(productId2);
        assertThat(likeCount).isEqualTo(1);
        assertThat(successCount.get()).isEqualTo(1);
        assertThat(failCount.get()).isEqualTo(1);

        // [추가] product.likeCount도 1 — 실패한 스레드는 increaseLikeCount에 도달하지 않음
        ProductModel product = productService.getById(productId2);
        assertThat(product.getLikeCount()).isEqualTo(1);
    }
}
