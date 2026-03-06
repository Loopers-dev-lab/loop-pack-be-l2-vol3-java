package com.loopers.concurrency;

import com.loopers.application.brand.BrandCommand;
import com.loopers.application.brand.BrandService;
import com.loopers.application.coupon.CouponCommand;
import com.loopers.application.coupon.CouponService;
import com.loopers.application.like.LikeFacade;
import com.loopers.application.order.OrderCommand;
import com.loopers.application.order.OrderFacade;
import com.loopers.application.product.ProductCommand;
import com.loopers.application.product.ProductService;
import com.loopers.domain.coupon.Coupon;
import com.loopers.domain.coupon.CouponRepository;
import com.loopers.domain.coupon.CouponType;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductRepository;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class ConcurrencyIntegrationTest {

    @Autowired
    private BrandService brandService;

    @Autowired
    private CouponService couponService;

    @Autowired
    private CouponRepository couponRepository;

    @Autowired
    private LikeFacade likeFacade;

    @Autowired
    private OrderFacade orderFacade;

    @Autowired
    private ProductService productService;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @Nested
    class 좋아요_동시성 {

        @Test
        void 동시에_여러_유저가_좋아요해도_좋아요수가_정상_반영된다() throws InterruptedException {
            Long brandId = brandService.register(BrandCommand.Register.of("나이키", "스포츠 브랜드")).getId();
            Product product = productService.register(
                    ProductCommand.Register.of(brandId, "운동화", new BigDecimal("50000"), 100, "편한 운동화"));
            Long productId = product.getId();

            int threadCount = 10;
            ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
            CountDownLatch latch = new CountDownLatch(threadCount);

            for (int i = 0; i < threadCount; i++) {
                long userId = i + 1;
                executorService.submit(() -> {
                    try {
                        likeFacade.like(userId, productId);
                    } finally {
                        latch.countDown();
                    }
                });
            }

            latch.await();
            executorService.shutdown();

            Product found = productRepository.findById(productId).orElseThrow();
            assertThat(found.getLikeCount()).isEqualTo(threadCount);
        }

        @Test
        void 동시에_여러_유저가_좋아요_취소해도_좋아요수가_정상_반영된다() throws InterruptedException {
            Long brandId = brandService.register(BrandCommand.Register.of("나이키", "스포츠 브랜드")).getId();
            Product product = productService.register(
                    ProductCommand.Register.of(brandId, "운동화", new BigDecimal("50000"), 100, "편한 운동화"));
            Long productId = product.getId();

            int threadCount = 10;
            for (int i = 0; i < threadCount; i++) {
                likeFacade.like((long) (i + 1), productId);
            }

            ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
            CountDownLatch latch = new CountDownLatch(threadCount);

            for (int i = 0; i < threadCount; i++) {
                long userId = i + 1;
                executorService.submit(() -> {
                    try {
                        likeFacade.unlike(userId, productId);
                    } finally {
                        latch.countDown();
                    }
                });
            }

            latch.await();
            executorService.shutdown();

            Product found = productRepository.findById(productId).orElseThrow();
            assertThat(found.getLikeCount()).isEqualTo(0);
        }
    }

    @Nested
    class 재고_동시성 {

        @Test
        void 동시에_여러_주문이_들어와도_재고가_정상_차감된다() throws InterruptedException {
            Long brandId = brandService.register(BrandCommand.Register.of("나이키", "스포츠 브랜드")).getId();
            Product product = productService.register(
                    ProductCommand.Register.of(brandId, "운동화", new BigDecimal("50000"), 100, "편한 운동화"));
            Long productId = product.getId();

            int threadCount = 10;
            ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
            CountDownLatch latch = new CountDownLatch(threadCount);
            List<Exception> exceptions = Collections.synchronizedList(new ArrayList<>());

            for (int i = 0; i < threadCount; i++) {
                long userId = i + 1;
                executorService.submit(() -> {
                    try {
                        orderFacade.placeOrder(userId, OrderCommand.Place.of(
                                List.of(OrderCommand.PlaceItem.of(productId, 1))
                        ));
                    } catch (Exception e) {
                        exceptions.add(e);
                    } finally {
                        latch.countDown();
                    }
                });
            }

            latch.await();
            executorService.shutdown();

            Product found = productRepository.findById(productId).orElseThrow();
            assertThat(found.getStockQuantity()).isEqualTo(90);
            assertThat(exceptions).isEmpty();
        }

        @Test
        void 재고보다_많은_동시_주문이_들어오면_일부만_성공한다() throws InterruptedException {
            Long brandId = brandService.register(BrandCommand.Register.of("나이키", "스포츠 브랜드")).getId();
            Product product = productService.register(
                    ProductCommand.Register.of(brandId, "운동화", new BigDecimal("50000"), 5, "편한 운동화"));
            Long productId = product.getId();

            int threadCount = 10;
            ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
            CountDownLatch latch = new CountDownLatch(threadCount);
            List<Exception> exceptions = Collections.synchronizedList(new ArrayList<>());

            for (int i = 0; i < threadCount; i++) {
                long userId = i + 1;
                executorService.submit(() -> {
                    try {
                        orderFacade.placeOrder(userId, OrderCommand.Place.of(
                                List.of(OrderCommand.PlaceItem.of(productId, 1))
                        ));
                    } catch (Exception e) {
                        exceptions.add(e);
                    } finally {
                        latch.countDown();
                    }
                });
            }

            latch.await();
            executorService.shutdown();

            Product found = productRepository.findById(productId).orElseThrow();
            assertThat(found.getStockQuantity()).isGreaterThanOrEqualTo(0);
            assertThat(exceptions).isNotEmpty();
            int successCount = threadCount - exceptions.size();
            assertThat(found.getStockQuantity()).isEqualTo(5 - successCount);
        }
    }

    @Nested
    class 쿠폰_발급_동시성 {

        @Test
        void 동시_발급_요청에도_발급_수량이_정확히_관리된다() throws InterruptedException {
            Coupon coupon = couponService.register(CouponCommand.Register.of(
                    "쿠폰", CouponType.FIXED, 1000,
                    null, 100, LocalDateTime.now().plusDays(7)
            ));

            int threadCount = 10;
            ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
            CountDownLatch latch = new CountDownLatch(threadCount);
            List<Exception> exceptions = Collections.synchronizedList(new ArrayList<>());

            for (int i = 0; i < threadCount; i++) {
                executorService.submit(() -> {
                    try {
                        couponService.issue(coupon.getId());
                    } catch (Exception e) {
                        exceptions.add(e);
                    } finally {
                        latch.countDown();
                    }
                });
            }
            latch.await();
            executorService.shutdown();

            Coupon found = couponRepository.findById(coupon.getId()).orElseThrow();
            assertThat(found.getIssuedCount()).isEqualTo(threadCount);
            assertThat(exceptions).isEmpty();
        }

        @Test
        void 최대_발급_수량보다_많은_동시_발급_요청이_들어오면_일부만_성공한다() throws InterruptedException {
            int maxIssueCount = 5;
            Coupon coupon = couponService.register(CouponCommand.Register.of(
                    "쿠폰", CouponType.FIXED, 1000,
                    null, maxIssueCount, LocalDateTime.now().plusDays(7)
            ));

            int threadCount = 10;
            ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
            CountDownLatch latch = new CountDownLatch(threadCount);
            List<Exception> exceptions = Collections.synchronizedList(new ArrayList<>());

            for (int i = 0; i < threadCount; i++) {
                executorService.submit(() -> {
                    try {
                        couponService.issue(coupon.getId());
                    } catch (Exception e) {
                        exceptions.add(e);
                    } finally {
                        latch.countDown();
                    }
                });
            }
            latch.await();
            executorService.shutdown();

            Coupon found = couponRepository.findById(coupon.getId()).orElseThrow();
            assertThat(found.getIssuedCount()).isEqualTo(maxIssueCount);
            assertThat(exceptions).hasSize(threadCount - maxIssueCount);
        }
    }
}
