package com.loopers.concurrency;

import com.loopers.application.like.LikeService;
import com.loopers.domain.like.LikeRepository;
import com.loopers.domain.member.Member;
import com.loopers.domain.member.MemberRepository;
import com.loopers.domain.product.Brand;
import com.loopers.domain.product.BrandRepository;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductRepository;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class LikeConcurrencyTest {

    @Autowired
    private LikeService likeService;

    @Autowired
    private LikeRepository likeRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private BrandRepository brandRepository;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("100명이 동시에 같은 상품에 좋아요하면 좋아요 수가 100이 된다")
    @Test
    void concurrentLikes() throws InterruptedException {
        Brand brand = brandRepository.save(new Brand("브랜드"));
        Product product = productRepository.save(new Product(brand.getId(), "상품", 10_000L, 100));
        Long productId = product.getId();

        int threadCount = 100;
        ExecutorService executorService = Executors.newFixedThreadPool(20);
        CountDownLatch latch = new CountDownLatch(threadCount);
        List<Boolean> results = Collections.synchronizedList(new ArrayList<>());

        for (int i = 0; i < threadCount; i++) {
            final long memberId = i + 1;
            memberRepository.save(new Member("user" + memberId, "password", "사용자" + memberId, "2000-01-01", "user" + memberId + "@test.com"));
            executorService.submit(() -> {
                try {
                    likeService.like(memberId, productId);
                    results.add(true);
                } catch (Exception e) {
                    results.add(false);
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executorService.shutdown();

        long likeCount = likeRepository.countByProductId(productId);
        assertThat(likeCount).isEqualTo(100);
    }

    @DisplayName("50명이 좋아요하고 다른 50명이 좋아요 후 취소하면 좋아요 수가 50이 된다")
    @Test
    void concurrentLikesAndUnlikes() throws InterruptedException {
        Brand brand = brandRepository.save(new Brand("브랜드"));
        Product product = productRepository.save(new Product(brand.getId(), "상품", 10_000L, 100));
        Long productId = product.getId();

        for (int i = 51; i <= 100; i++) {
            final long memberId = i;
            memberRepository.save(new Member("user" + memberId, "password", "사용자" + memberId, "2000-01-01", "user" + memberId + "@test.com"));
            likeService.like(memberId, productId);
        }

        int threadCount = 100;
        ExecutorService executorService = Executors.newFixedThreadPool(20);
        CountDownLatch latch = new CountDownLatch(threadCount);

        for (int i = 1; i <= 50; i++) {
            final long memberId = i;
            memberRepository.save(new Member("newuser" + memberId, "password", "신규" + memberId, "2000-01-01", "new" + memberId + "@test.com"));
        }

        for (int i = 1; i <= 50; i++) {
            final long newMemberId = 100 + i;
            final long existingMemberId = 50 + i;

            executorService.submit(() -> {
                try {
                    Member newMember = memberRepository.findByLoginId("newuser" + (newMemberId - 100)).orElseThrow();
                    likeService.like(newMember.getId(), productId);
                } catch (Exception ignored) {
                } finally {
                    latch.countDown();
                }
            });

            executorService.submit(() -> {
                try {
                    likeService.unlike(existingMemberId, productId);
                } catch (Exception ignored) {
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executorService.shutdown();

        long likeCount = likeRepository.countByProductId(productId);
        assertThat(likeCount).isEqualTo(50);
    }
}
