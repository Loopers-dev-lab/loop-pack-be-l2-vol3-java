package com.loopers.application.concurrency;

import com.loopers.application.coupon.CouponFacade;
import com.loopers.application.like.LikeFacade;
import com.loopers.application.order.OrderCreateCommand;
import com.loopers.application.order.OrderFacade;
import com.loopers.application.product.ProductAppService;
import com.loopers.domain.brand.Brand;
import com.loopers.domain.brand.BrandRepository;
import com.loopers.domain.common.Money;
import com.loopers.domain.coupon.Coupon;
import com.loopers.domain.coupon.CouponRepository;
import com.loopers.domain.coupon.DiscountType;
import com.loopers.domain.coupon.IssuedCoupon;
import com.loopers.domain.coupon.IssuedCouponRepository;
import com.loopers.domain.coupon.IssuedCouponStatus;
import com.loopers.domain.member.Member;
import com.loopers.domain.member.MemberRepository;
import com.loopers.domain.member.vo.BirthDate;
import com.loopers.domain.member.vo.Email;
import com.loopers.domain.member.vo.MemberId;
import com.loopers.domain.member.vo.Name;
import com.loopers.domain.member.vo.Password;
import com.loopers.domain.order.Order;
import com.loopers.domain.order.OrderRepository;
import com.loopers.domain.order.OrderStatus;
import com.loopers.domain.product.Option;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductRepository;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@DisplayName("동시성 테스트")
class ConcurrencyTest {

    @Autowired private ProductAppService productAppService;
    @Autowired private ProductRepository productRepository;
    @Autowired private BrandRepository brandRepository;
    @Autowired private CouponRepository couponRepository;
    @Autowired private CouponFacade couponFacade;
    @Autowired private IssuedCouponRepository issuedCouponRepository;
    @Autowired private OrderFacade orderFacade;
    @Autowired private OrderRepository orderRepository;
    @Autowired private MemberRepository memberRepository;
    @Autowired private LikeFacade likeFacade;
    @Autowired private DatabaseCleanUp databaseCleanUp;

    private Long brandId;
    private Long productId;
    private Long optionId;

    @BeforeEach
    void setUp() {
        Brand brand = brandRepository.save(Brand.create("테스트 브랜드"));
        brandId = brand.getId();
        Product product = productAppService.create(brandId, "테스트 상품", Money.of(10000L));
        productId = product.getId();
        Option option = productAppService.createOption(productId, "기본 옵션", Money.of(1000L), 10);
        optionId = option.getId();
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @Nested
    @DisplayName("동일 상품 동시 주문 (재고 차감)")
    class ConcurrentStockDecreaseTest {

        @Test
        @DisplayName("재고 10개, 10 스레드 동시 주문 → 재고 정확히 0")
        void concurrentStockDecrease() throws InterruptedException {
            int threadCount = 10;
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch latch = new CountDownLatch(threadCount);
            AtomicInteger successCount = new AtomicInteger(0);
            AtomicInteger failCount = new AtomicInteger(0);

            for (int i = 0; i < threadCount; i++) {
                final long userId = i + 1;
                Member member = createTestMember("user" + userId, userId);

                executor.submit(() -> {
                    try {
                        startLatch.await();
                        OrderCreateCommand command = new OrderCreateCommand(
                                userId,
                                List.of(new OrderCreateCommand.OrderItemCommand(optionId, 1))
                        );
                        orderFacade.createOrder(command);
                        successCount.incrementAndGet();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } catch (Exception e) {
                        failCount.incrementAndGet();
                    } finally {
                        latch.countDown();
                    }
                });
            }

            startLatch.countDown();
            latch.await(30, TimeUnit.SECONDS);
            executor.shutdown();

            Option updatedOption = productAppService.getOptionById(optionId);
            assertThat(successCount.get()).isEqualTo(10);
            assertThat(failCount.get()).isZero();
            assertThat(updatedOption.getStock()).isZero();
        }
    }

    @Nested
    @DisplayName("동일 쿠폰 동시 발급")
    class ConcurrentCouponIssueTest {

        @Test
        @DisplayName("총 수량 5장, 20 스레드 동시 발급 → 5건만 성공")
        void concurrentCouponIssue() throws InterruptedException {
            Coupon coupon = couponRepository.save(Coupon.create(
                    "선착순 쿠폰", DiscountType.FIXED, Money.of(1000L), Money.zero(), null, 5,
                    ZonedDateTime.now().minusDays(1), ZonedDateTime.now().plusDays(30)
            ));
            Long couponId = coupon.getId();

            int threadCount = 20;
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch latch = new CountDownLatch(threadCount);
            AtomicInteger successCount = new AtomicInteger(0);
            AtomicInteger failCount = new AtomicInteger(0);

            for (int i = 0; i < threadCount; i++) {
                final long userId = i + 1;
                executor.submit(() -> {
                    try {
                        startLatch.await();
                        couponFacade.issueCoupon(couponId, userId);
                        successCount.incrementAndGet();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } catch (Exception e) {
                        failCount.incrementAndGet();
                    } finally {
                        latch.countDown();
                    }
                });
            }

            startLatch.countDown();
            latch.await(30, TimeUnit.SECONDS);
            executor.shutdown();

            assertThat(successCount.get()).isEqualTo(5);
            assertThat(failCount.get()).isEqualTo(15);

            Coupon updatedCoupon = couponRepository.findById(couponId).orElseThrow();
            assertThat(updatedCoupon.getIssuedQuantity()).isEqualTo(5);
        }
    }

    @Nested
    @DisplayName("동일 쿠폰 동시 사용")
    class ConcurrentCouponUseTest {

        @Test
        @DisplayName("10 스레드가 1장의 쿠폰으로 동시 주문 → 1건만 성공")
        void concurrentCouponUse() throws InterruptedException {
            Coupon coupon = couponRepository.save(Coupon.create(
                    "테스트 쿠폰", DiscountType.FIXED, Money.of(1000L), Money.zero(), null, 100,
                    ZonedDateTime.now().minusDays(1), ZonedDateTime.now().plusDays(30)
            ));
            Long couponId = coupon.getId();

            // 특정 유저에게 쿠폰 발급
            Member member = createTestMember("couponuser", 100L);
            couponFacade.issueCoupon(couponId, member.getId());

            // 옵션 재고 충분히 설정
            Option bigStockOption = productAppService.createOption(productId, "대용량 옵션", Money.zero(), 100);
            Long bigOptionId = bigStockOption.getId();

            int threadCount = 10;
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch latch = new CountDownLatch(threadCount);
            AtomicInteger successCount = new AtomicInteger(0);
            AtomicInteger failCount = new AtomicInteger(0);

            for (int i = 0; i < threadCount; i++) {
                executor.submit(() -> {
                    try {
                        startLatch.await();
                        OrderCreateCommand command = new OrderCreateCommand(
                                member.getId(),
                                List.of(new OrderCreateCommand.OrderItemCommand(bigOptionId, 1)),
                                couponId
                        );
                        orderFacade.createOrder(command);
                        successCount.incrementAndGet();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } catch (Exception e) {
                        failCount.incrementAndGet();
                    } finally {
                        latch.countDown();
                    }
                });
            }

            startLatch.countDown();
            latch.await(30, TimeUnit.SECONDS);
            executor.shutdown();

            assertThat(successCount.get()).isEqualTo(1);
            assertThat(failCount.get()).isEqualTo(9);

            IssuedCoupon usedCoupon = issuedCouponRepository.findByCouponIdAndUserId(couponId, member.getId())
                    .orElseThrow();
            assertThat(usedCoupon.getStatus()).isEqualTo(IssuedCouponStatus.USED);
        }
    }

    @Nested
    @DisplayName("동일 상품 동시 좋아요")
    class ConcurrentLikeTest {

        @Test
        @DisplayName("N 스레드 동시 좋아요 → likeCount 정확히 반영")
        void concurrentLike() throws InterruptedException {
            int threadCount = 10;
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch latch = new CountDownLatch(threadCount);
            AtomicInteger successCount = new AtomicInteger(0);
            AtomicInteger failCount = new AtomicInteger(0);

            for (int i = 0; i < threadCount; i++) {
                final long userId = i + 1;
                executor.submit(() -> {
                    try {
                        startLatch.await();
                        likeFacade.toggleLike(userId, productId);
                        successCount.incrementAndGet();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } catch (Exception e) {
                        failCount.incrementAndGet();
                    } finally {
                        latch.countDown();
                    }
                });
            }

            startLatch.countDown();
            latch.await(30, TimeUnit.SECONDS);
            executor.shutdown();

            assertThat(successCount.get()).isEqualTo(10);
            assertThat(failCount.get()).isZero();

            Product updatedProduct = productRepository.findById(productId).orElseThrow();
            assertThat(updatedProduct.getLikeCount()).isEqualTo(successCount.get());
        }
    }

    @Nested
    @DisplayName("동일 주문 동시 취소")
    class ConcurrentOrderCancelTest {

        @Test
        @DisplayName("동일 주문 2스레드 동시 취소 → 1건만 성공")
        void concurrentOrderCancel() throws InterruptedException {
            Member member = createTestMember("canceluser", 300L);

            Option cancelOption = productAppService.createOption(productId, "취소 옵션", Money.zero(), 100);
            Long cancelOptionId = cancelOption.getId();

            OrderCreateCommand command = new OrderCreateCommand(
                    member.getId(),
                    List.of(new OrderCreateCommand.OrderItemCommand(cancelOptionId, 1))
            );
            Order order = orderFacade.createOrder(command);
            Long orderId = order.getId();

            int threadCount = 2;
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch latch = new CountDownLatch(threadCount);
            AtomicInteger successCount = new AtomicInteger(0);
            AtomicInteger failCount = new AtomicInteger(0);

            for (int i = 0; i < threadCount; i++) {
                executor.submit(() -> {
                    try {
                        startLatch.await();
                        orderFacade.cancelOrder(member.getId(), orderId);
                        successCount.incrementAndGet();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } catch (Exception e) {
                        failCount.incrementAndGet();
                    } finally {
                        latch.countDown();
                    }
                });
            }

            startLatch.countDown();
            latch.await(30, TimeUnit.SECONDS);
            executor.shutdown();

            assertThat(successCount.get()).isEqualTo(1);
            assertThat(failCount.get()).isEqualTo(1);

            Order canceledOrder = orderRepository.findById(orderId).orElseThrow();
            assertThat(canceledOrder.getStatus()).isEqualTo(OrderStatus.CANCELED);

            Option updatedOption = productAppService.getOptionById(cancelOptionId);
            assertThat(updatedOption.getStock()).isEqualTo(100);
        }
    }

    @Nested
    @DisplayName("재고 부족 동시 주문")
    class ConcurrentPartialStockTest {

        @Test
        @DisplayName("재고 5개, 10 스레드 동시 주문 → 5건만 성공")
        void concurrentPartialStockDecrease() throws InterruptedException {
            Option limitedOption = productAppService.createOption(productId, "제한 옵션", Money.of(1000L), 5);
            Long limitedOptionId = limitedOption.getId();

            int threadCount = 10;
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch latch = new CountDownLatch(threadCount);
            AtomicInteger successCount = new AtomicInteger(0);
            AtomicInteger failCount = new AtomicInteger(0);

            for (int i = 0; i < threadCount; i++) {
                Member member = createTestMember("ptu" + i, 400L + i);
                final Long memberId = member.getId();

                executor.submit(() -> {
                    try {
                        startLatch.await();
                        OrderCreateCommand command = new OrderCreateCommand(
                                memberId,
                                List.of(new OrderCreateCommand.OrderItemCommand(limitedOptionId, 1))
                        );
                        orderFacade.createOrder(command);
                        successCount.incrementAndGet();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } catch (Exception e) {
                        failCount.incrementAndGet();
                    } finally {
                        latch.countDown();
                    }
                });
            }

            startLatch.countDown();
            latch.await(30, TimeUnit.SECONDS);
            executor.shutdown();

            assertThat(successCount.get()).isEqualTo(5);
            assertThat(failCount.get()).isEqualTo(5);

            Option updatedOption = productAppService.getOptionById(limitedOptionId);
            assertThat(updatedOption.getStock()).isZero();
        }
    }

    private Member createTestMember(String memberId, Long seed) {
        Member member = Member.create(
                new MemberId(memberId),
                Password.ofEncoded("encoded:Password1!"),
                new Name("테스트" + seed),
                new Email(memberId + "@test.com"),
                new BirthDate("1997-01-01")
        );
        return memberRepository.save(member);
    }
}
