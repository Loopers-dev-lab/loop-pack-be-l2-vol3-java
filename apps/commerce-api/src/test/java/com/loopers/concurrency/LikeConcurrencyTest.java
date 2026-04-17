package com.loopers.concurrency;

import com.loopers.application.like.LikeFacade;
import com.loopers.domain.brand.Brand;
import com.loopers.domain.brand.BrandRepository;
import com.loopers.domain.like.LikeRepository;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductRepository;
import com.loopers.domain.product.vo.Price;
import com.loopers.domain.product.vo.Stock;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
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
    private LikeFacade likeFacade;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private BrandRepository brandRepository;

    @Autowired
    private LikeRepository likeRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("동일 상품에 여러 명이 동시에 좋아요하면 모두 성공하고 Like 레코드 + Product.likeCount가 정확하다")
    @Test
    void concurrentLikes_allSucceed_andCountIsCorrect() throws InterruptedException {
        // arrange
        int threadCount = 100;
        Brand brand = brandRepository.save(new Brand("나이키", "스포츠 브랜드"));
        Product product = productRepository.save(
            new Product(brand.getId(), "에어맥스", new Price(100000), new Stock(10)));
        Long productId = product.getId();

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);

        // act
        for (int i = 0; i < threadCount; i++) {
            long memberId = i + 1;
            executor.submit(() -> {
                try {
                    likeFacade.addLike(memberId, productId);
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    // ignore
                } finally {
                    latch.countDown();
                }
            });
        }
        latch.await();
        executor.shutdown();

        // assert — Like 레코드는 즉시 확인, likeCount는 비동기 리스너 완료 대기
        long actualLikeRecords = likeRepository.countByProductId(productId);
        assertThat(successCount.get()).isEqualTo(threadCount);
        assertThat(actualLikeRecords).isEqualTo(threadCount);

        // likeCount는 @Async AFTER_COMMIT 리스너에서 갱신 → 폴링으로 대기
        waitForLikeCount(productId, threadCount);
    }

    @DisplayName("동일 상품에 여러 명이 좋아요 후 일부가 취소하면 Like 레코드 수와 Product.likeCount가 일치한다")
    @Test
    void concurrentLikeAndUnlike_countsCorrectly() throws InterruptedException {
        // arrange
        int likeCount = 100;
        Brand brand = brandRepository.save(new Brand("나이키", "스포츠 브랜드"));
        Product product = productRepository.save(
            new Product(brand.getId(), "에어맥스", new Price(100000), new Stock(10)));
        Long productId = product.getId();

        // 먼저 100명이 좋아요
        ExecutorService executor1 = Executors.newFixedThreadPool(likeCount);
        CountDownLatch latch1 = new CountDownLatch(likeCount);
        for (int i = 0; i < likeCount; i++) {
            long memberId = i + 1;
            executor1.submit(() -> {
                try {
                    likeFacade.addLike(memberId, productId);
                } catch (Exception e) {
                    // ignore
                } finally {
                    latch1.countDown();
                }
            });
        }
        latch1.await();
        executor1.shutdown();

        // addLike 비동기 리스너 완료 대기
        waitForLikeCount(productId, likeCount);

        // 5명이 동시에 좋아요 취소
        int unlikeCount = 5;
        ExecutorService executor2 = Executors.newFixedThreadPool(unlikeCount);
        CountDownLatch latch2 = new CountDownLatch(unlikeCount);
        for (int i = 0; i < unlikeCount; i++) {
            long memberId = i + 1;
            executor2.submit(() -> {
                try {
                    likeFacade.removeLike(memberId, productId);
                } catch (Exception e) {
                    // ignore
                } finally {
                    latch2.countDown();
                }
            });
        }
        latch2.await();
        executor2.shutdown();

        // assert — Like 레코드 수와 Product.likeCount가 일치해야 한다
        long actualLikeRecords = likeRepository.countByProductId(productId);
        assertThat(actualLikeRecords).isEqualTo(likeCount - unlikeCount);

        waitForLikeCount(productId, (int) actualLikeRecords);
    }

    /**
     * @Async AFTER_COMMIT 리스너의 likeCount 갱신 완료를 폴링으로 대기한다.
     * 최대 10초 (100ms × 100회) 대기.
     */
    private void waitForLikeCount(Long productId, int expected) throws InterruptedException {
        for (int attempt = 0; attempt < 100; attempt++) {
            Product p = productRepository.findById(productId).orElseThrow();
            if (p.getLikeCount() == expected) {
                return;
            }
            Thread.sleep(100);
        }
        // 최종 assert (실패 시 명확한 메시지)
        Product p = productRepository.findById(productId).orElseThrow();
        assertThat(p.getLikeCount())
            .as("likeCount가 %d이어야 하지만 비동기 리스너가 시간 내 완료되지 않음", expected)
            .isEqualTo(expected);
    }
}
