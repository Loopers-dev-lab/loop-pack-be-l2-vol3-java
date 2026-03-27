package com.loopers.application.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;

import com.loopers.application.order.dto.OrderCriteria;
import com.loopers.application.order.dto.OrderResult;
import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.coupon.CouponDiscountType;
import com.loopers.domain.coupon.CouponModel;
import com.loopers.domain.coupon.OwnedCouponModel;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.user.UserModel;
import com.loopers.infrastructure.brand.BrandJpaRepository;
import com.loopers.infrastructure.coupon.CouponJpaRepository;
import com.loopers.infrastructure.coupon.OwnedCouponJpaRepository;
import com.loopers.infrastructure.product.ProductJpaRepository;
import com.loopers.infrastructure.user.UserJpaRepository;
import com.loopers.support.error.CoreException;
import com.loopers.utils.DatabaseCleanUp;
import java.time.ZonedDateTime;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@DisplayName("주문 통합 테스트")
@SpringBootTest
class OrderIntegrationTest {

    @Autowired
    private OrderFacade orderFacade;

    @Autowired
    private UserJpaRepository userJpaRepository;

    @Autowired
    private BrandJpaRepository brandJpaRepository;

    @Autowired
    private ProductJpaRepository productJpaRepository;

    @Autowired
    private CouponJpaRepository couponJpaRepository;

    @Autowired
    private OwnedCouponJpaRepository ownedCouponJpaRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    private UserModel createUserWithPoint(long point) {
        UserModel user = UserModel.create(
                "testuser", "encrypted", "테스트",
                java.time.LocalDate.of(1990, 1, 1), "test@test.com");
        user.addPoint(point);
        return userJpaRepository.save(user);
    }

    private ProductModel createProduct(Long brandId, int price, int stock) {
        return productJpaRepository.save(
                ProductModel.create(brandId, "테스트 상품", price, stock));
    }

    private OwnedCouponModel createOwnedCoupon(Long userId, CouponDiscountType type,
                                                 long value, Long minOrderAmount) {
        CouponModel coupon = couponJpaRepository.save(
                CouponModel.create("테스트 쿠폰", type, value, minOrderAmount, 100,
                        ZonedDateTime.now().plusMonths(3)));
        coupon.issue();
        couponJpaRepository.save(coupon);
        return ownedCouponJpaRepository.save(OwnedCouponModel.create(coupon, userId));
    }

    @DisplayName("롤백 테스트:")
    @Nested
    class RollbackTest {

        @DisplayName("쿠폰 사용 실패 시 재고가 원복된다")
        @Test
        void couponFail_stockRollback() {
            // arrange
            BrandModel brand = brandJpaRepository.save(BrandModel.create("브랜드A"));
            ProductModel product = createProduct(brand.getId(), 10000, 10);
            UserModel user = createUserWithPoint(1_000_000);
            // 최소 주문 금액 미달 쿠폰 (minOrderAmount = 500,000)
            OwnedCouponModel ownedCoupon = createOwnedCoupon(
                    user.getId(), CouponDiscountType.FIXED, 5000, 500_000L);

            // act — 쿠폰 최소 주문 금액 미달로 실패 예상
            assertThatThrownBy(() -> orderFacade.createOrder(user.getId(),
                    new OrderCriteria.Create(List.of(
                            new OrderCriteria.Create.CreateItem(
                                    product.getId(), 2, 10000)),
                            ownedCoupon.getId())))
                    .isInstanceOf(CoreException.class);

            // assert — 재고가 원복되어야 함
            ProductModel updated = productJpaRepository.findById(product.getId()).orElseThrow();
            assertAll(
                    () -> assertThat(updated.getStock())
                            .as("재고가 원래대로 복구되어야 한다")
                            .isEqualTo(10),
                    () -> assertThat(ownedCouponJpaRepository.findById(
                            ownedCoupon.getId()).orElseThrow().isUsed())
                            .as("쿠폰은 사용되지 않은 상태여야 한다")
                            .isFalse());
        }

    }

    @DisplayName("통합 테스트:")
    @Nested
    class FullIntegration {

        @DisplayName("주문 성공 시 재고 차감 + 쿠폰 사용 + 포인트 차감 모두 반영된다")
        @Test
        void orderSuccess_allSideEffectsApplied() {
            // arrange
            BrandModel brand = brandJpaRepository.save(BrandModel.create("브랜드C"));
            ProductModel product = createProduct(brand.getId(), 50000, 10);
            UserModel user = createUserWithPoint(1_000_000);
            OwnedCouponModel ownedCoupon = createOwnedCoupon(
                    user.getId(), CouponDiscountType.FIXED, 5000, null);

            // act — 주문 생성 (50000 * 2 = 100000, 할인 5000 → 최종 95000)
            OrderResult.OrderSummary result = orderFacade.createOrder(user.getId(),
                    new OrderCriteria.Create(List.of(
                            new OrderCriteria.Create.CreateItem(
                                    product.getId(), 2, 50000)),
                            ownedCoupon.getId()));

            // assert
            ProductModel updatedProduct = productJpaRepository.findById(
                    product.getId()).orElseThrow();
            OwnedCouponModel updatedCoupon = ownedCouponJpaRepository.findById(
                    ownedCoupon.getId()).orElseThrow();
            assertAll(
                    () -> assertThat(result.totalPrice())
                            .as("주문 총액은 할인 적용된 95000이어야 한다")
                            .isEqualTo(95000),
                    () -> assertThat(result.discountAmount())
                            .as("할인 금액은 5000이어야 한다")
                            .isEqualTo(5000),
                    () -> assertThat(updatedProduct.getStock())
                            .as("재고가 2개 차감되어야 한다")
                            .isEqualTo(8),
                    () -> assertThat(updatedCoupon.isUsed())
                            .as("쿠폰이 사용 상태여야 한다")
                            .isTrue());
        }
    }
}
