package com.loopers.concurrency;

import com.loopers.application.order.OrderService;
import com.loopers.domain.coupon.CouponStatus;
import com.loopers.domain.coupon.CouponTemplate;
import com.loopers.domain.coupon.CouponTemplateRepository;
import com.loopers.domain.coupon.CouponType;
import com.loopers.domain.coupon.IssuedCoupon;
import com.loopers.domain.coupon.IssuedCouponRepository;
import com.loopers.domain.member.Member;
import com.loopers.domain.member.MemberRepository;
import com.loopers.domain.order.OrderDomainService;
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

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class CouponConcurrencyTest {

    @Autowired
    private OrderService orderService;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private BrandRepository brandRepository;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private CouponTemplateRepository couponTemplateRepository;

    @Autowired
    private IssuedCouponRepository issuedCouponRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("동일한 쿠폰으로 5개 스레드가 동시에 주문해도 쿠폰은 단 1번만 사용된다")
    @Test
    void concurrentCouponUse_onlyOneSucceeds() throws InterruptedException {
        Brand brand = brandRepository.save(new Brand("브랜드"));
        Product product = productRepository.save(new Product(brand.getId(), "상품", 10_000L, 100));
        Long productId = product.getId();

        Member member = memberRepository.save(new Member("user1", "password", "사용자", "2000-01-01", "user@test.com"));
        Long memberId = member.getId();

        CouponTemplate template = couponTemplateRepository.save(
            new CouponTemplate("1000원 할인", CouponType.FIXED, 1000, null, ZonedDateTime.now().plusDays(30))
        );
        IssuedCoupon issuedCoupon = issuedCouponRepository.save(new IssuedCoupon(template.getId(), memberId));
        Long couponId = issuedCoupon.getId();

        int threadCount = 5;
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        List<Boolean> results = Collections.synchronizedList(new ArrayList<>());

        for (int i = 0; i < threadCount; i++) {
            executorService.submit(() -> {
                try {
                    orderService.placeOrder(memberId, List.of(
                        new OrderDomainService.OrderLineRequest(productId, 1)
                    ), couponId);
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

        long successCount = results.stream().filter(r -> r).count();
        assertThat(successCount).isEqualTo(1);

        IssuedCoupon updatedCoupon = issuedCouponRepository.findById(couponId).orElseThrow();
        assertThat(updatedCoupon.getStatus()).isEqualTo(CouponStatus.USED);
    }
}
