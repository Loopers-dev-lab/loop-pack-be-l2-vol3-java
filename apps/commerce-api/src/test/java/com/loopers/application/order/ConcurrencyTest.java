package com.loopers.application.order;

import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.coupon.CouponModel;
import com.loopers.domain.coupon.CouponType;
import com.loopers.domain.coupon.UserCouponModel;
import com.loopers.domain.like.LikeService;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductStatus;
import com.loopers.domain.user.UserModel;
import com.loopers.infrastructure.brand.BrandJpaRepository;
import com.loopers.infrastructure.coupon.CouponJpaRepository;
import com.loopers.infrastructure.coupon.UserCouponJpaRepository;
import com.loopers.infrastructure.like.LikeJpaRepository;
import com.loopers.infrastructure.order.OrderJpaRepository;
import com.loopers.infrastructure.product.ProductJpaRepository;
import com.loopers.infrastructure.user.UserJpaRepository;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class ConcurrencyTest {

    @Autowired
    private OrderFacade orderFacade;

    @Autowired
    private LikeService likeService;

    @Autowired
    private UserJpaRepository userJpaRepository;

    @Autowired
    private BrandJpaRepository brandJpaRepository;

    @Autowired
    private ProductJpaRepository productJpaRepository;

    @Autowired
    private OrderJpaRepository orderJpaRepository;

    @Autowired
    private CouponJpaRepository couponJpaRepository;

    @Autowired
    private UserCouponJpaRepository userCouponJpaRepository;

    @Autowired
    private LikeJpaRepository likeJpaRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    private UserModel createUser(String loginId, String rawPassword, String name, String email) {
        String encodedPassword = passwordEncoder.encode(rawPassword);
        return userJpaRepository.save(
            UserModel.createWithEncodedPassword(loginId, encodedPassword, name, LocalDate.of(1990, 1, 15), email)
        );
    }

    private ProductModel createProduct(BrandModel brand, String name, Long price, int stock) {
        return productJpaRepository.save(
            new ProductModel(brand, name, price, name + " 설명", stock, ProductStatus.ON_SALE)
        );
    }

    @DisplayName("동시 재고 차감 테스트")
    @Nested
    class ConcurrentStockDeduction {

        @DisplayName("10개 스레드가 동시에 1개씩 주문하면, 재고가 정확히 10개 차감된다.")
        @Test
        void deductsStockCorrectly_whenConcurrentOrdersArePlaced() throws InterruptedException {
            // arrange
            createUser("testuser", "Test1234!", "홍길동", "test@example.com");
            BrandModel brand = brandJpaRepository.save(new BrandModel("나이키", "스포츠 브랜드"));
            ProductModel product = createProduct(brand, "에어맥스", 150000L, 100);

            int threadCount = 10;
            ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
            CountDownLatch latch = new CountDownLatch(threadCount);
            AtomicInteger successCount = new AtomicInteger(0);
            AtomicInteger failCount = new AtomicInteger(0);

            // act
            for (int i = 0; i < threadCount; i++) {
                executorService.submit(() -> {
                    try {
                        orderFacade.placeOrder(
                            "testuser", "Test1234!",
                            List.of(new OrderFacade.PlaceOrderItem(product.getId(), 1)),
                            null
                        );
                        successCount.incrementAndGet();
                    } catch (Exception e) {
                        failCount.incrementAndGet();
                    } finally {
                        latch.countDown();
                    }
                });
            }
            latch.await();
            executorService.shutdown();

            // assert
            ProductModel updatedProduct = productJpaRepository.findById(product.getId()).orElseThrow();
            assertThat(successCount.get()).isEqualTo(10);
            assertThat(failCount.get()).isEqualTo(0);
            assertThat(updatedProduct.getStockQuantity()).isEqualTo(90);
        }

        @DisplayName("재고 5개 상품에 10개 스레드가 동시 주문하면, 5건만 성공하고 재고는 0이 된다.")
        @Test
        void preventsOverselling_whenConcurrentOrdersExceedStock() throws InterruptedException {
            // arrange
            createUser("testuser", "Test1234!", "홍길동", "test@example.com");
            BrandModel brand = brandJpaRepository.save(new BrandModel("나이키", "스포츠 브랜드"));
            ProductModel product = createProduct(brand, "에어맥스", 150000L, 5);

            int threadCount = 10;
            ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
            CountDownLatch latch = new CountDownLatch(threadCount);
            AtomicInteger successCount = new AtomicInteger(0);
            AtomicInteger failCount = new AtomicInteger(0);

            // act
            for (int i = 0; i < threadCount; i++) {
                executorService.submit(() -> {
                    try {
                        orderFacade.placeOrder(
                            "testuser", "Test1234!",
                            List.of(new OrderFacade.PlaceOrderItem(product.getId(), 1)),
                            null
                        );
                        successCount.incrementAndGet();
                    } catch (Exception e) {
                        failCount.incrementAndGet();
                    } finally {
                        latch.countDown();
                    }
                });
            }
            latch.await();
            executorService.shutdown();

            // assert
            ProductModel updatedProduct = productJpaRepository.findById(product.getId()).orElseThrow();
            assertThat(successCount.get()).isEqualTo(5);
            assertThat(failCount.get()).isEqualTo(5);
            assertThat(updatedProduct.getStockQuantity()).isEqualTo(0);
        }
    }

    @DisplayName("동시 쿠폰 사용 테스트")
    @Nested
    class ConcurrentCouponUsage {

        @DisplayName("동일 유저쿠폰으로 10개 스레드가 동시 주문하면, 1건만 성공한다.")
        @Test
        void allowsOnlyOneOrder_whenConcurrentOrdersUseSameCoupon() throws InterruptedException {
            // arrange
            UserModel user = createUser("testuser", "Test1234!", "홍길동", "test@example.com");
            BrandModel brand = brandJpaRepository.save(new BrandModel("나이키", "스포츠 브랜드"));
            ProductModel product = createProduct(brand, "에어맥스", 150000L, 100);
            CouponModel coupon = couponJpaRepository.save(new CouponModel(
                "10% 할인 쿠폰", CouponType.RATE, 10L, 10000L, ZonedDateTime.now().plusDays(1)
            ));
            UserCouponModel userCoupon = userCouponJpaRepository.save(new UserCouponModel(user.getId(), coupon));

            int threadCount = 10;
            ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
            CountDownLatch latch = new CountDownLatch(threadCount);
            AtomicInteger successCount = new AtomicInteger(0);
            AtomicInteger failCount = new AtomicInteger(0);

            // act
            for (int i = 0; i < threadCount; i++) {
                executorService.submit(() -> {
                    try {
                        orderFacade.placeOrder(
                            "testuser", "Test1234!",
                            List.of(new OrderFacade.PlaceOrderItem(product.getId(), 1)),
                            userCoupon.getId()
                        );
                        successCount.incrementAndGet();
                    } catch (Exception e) {
                        failCount.incrementAndGet();
                    } finally {
                        latch.countDown();
                    }
                });
            }
            latch.await();
            executorService.shutdown();

            // assert
            assertThat(successCount.get()).isEqualTo(1);
            assertThat(failCount.get()).isEqualTo(9);
            assertThat(orderJpaRepository.count()).isEqualTo(1);
            ProductModel updatedProduct = productJpaRepository.findById(product.getId()).orElseThrow();
            assertThat(updatedProduct.getStockQuantity()).isEqualTo(99);
        }
    }

    @DisplayName("동시 좋아요 테스트")
    @Nested
    class ConcurrentLike {

        @DisplayName("10명의 서로 다른 사용자가 동시에 같은 상품에 좋아요하면, 모두 성공한다.")
        @Test
        void allSucceed_whenDifferentUsersConcurrentlyLikeSameProduct() throws InterruptedException {
            // arrange
            BrandModel brand = brandJpaRepository.save(new BrandModel("나이키", "스포츠 브랜드"));
            ProductModel product = createProduct(brand, "에어맥스", 150000L, 100);

            int threadCount = 10;
            Long[] userIds = new Long[threadCount];
            for (int i = 0; i < threadCount; i++) {
                UserModel user = createUser(
                    "user" + i, "Test1234!", "사용자", "user" + i + "@example.com"
                );
                userIds[i] = user.getId();
            }

            ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
            CountDownLatch latch = new CountDownLatch(threadCount);
            AtomicInteger successCount = new AtomicInteger(0);
            AtomicInteger failCount = new AtomicInteger(0);

            // act
            for (int i = 0; i < threadCount; i++) {
                final int index = i;
                executorService.submit(() -> {
                    try {
                        likeService.like(userIds[index], product.getId());
                        successCount.incrementAndGet();
                    } catch (Exception e) {
                        failCount.incrementAndGet();
                    } finally {
                        latch.countDown();
                    }
                });
            }
            latch.await();
            executorService.shutdown();

            // assert
            assertThat(successCount.get()).isEqualTo(10);
            assertThat(failCount.get()).isEqualTo(0);
            assertThat(likeJpaRepository.countByProductId(product.getId())).isEqualTo(10);
        }

        @DisplayName("같은 사용자가 10개 스레드에서 동시에 좋아요하면, 1건만 성공한다.")
        @Test
        void allowsOnlyOneLike_whenSameUserConcurrentlyLikes() throws InterruptedException {
            // arrange
            UserModel user = createUser("testuser", "Test1234!", "홍길동", "test@example.com");
            BrandModel brand = brandJpaRepository.save(new BrandModel("나이키", "스포츠 브랜드"));
            ProductModel product = createProduct(brand, "에어맥스", 150000L, 100);

            int threadCount = 10;
            ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
            CountDownLatch latch = new CountDownLatch(threadCount);
            AtomicInteger successCount = new AtomicInteger(0);
            AtomicInteger failCount = new AtomicInteger(0);

            // act
            for (int i = 0; i < threadCount; i++) {
                executorService.submit(() -> {
                    try {
                        likeService.like(user.getId(), product.getId());
                        successCount.incrementAndGet();
                    } catch (Exception e) {
                        failCount.incrementAndGet();
                    } finally {
                        latch.countDown();
                    }
                });
            }
            latch.await();
            executorService.shutdown();

            // assert
            assertThat(successCount.get()).isEqualTo(1);
            assertThat(failCount.get()).isEqualTo(9);
            assertThat(likeJpaRepository.countByProductId(product.getId())).isEqualTo(1);
        }
    }
}
