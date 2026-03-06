package com.loopers.application.order;

import com.loopers.domain.coupon.CouponStatus;
import com.loopers.domain.coupon.CouponTemplate;
import com.loopers.domain.coupon.CouponTemplateRepository;
import com.loopers.domain.coupon.DiscountType;
import com.loopers.domain.coupon.UserCoupon;
import com.loopers.domain.coupon.UserCouponRepository;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductRepository;
import com.loopers.domain.user.User;
import com.loopers.domain.user.UserRepository;
import com.loopers.support.error.CoreException;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest
class OrderFacadeRollbackTest {

    @Autowired
    private OrderFacade orderFacade;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserCouponRepository userCouponRepository;

    @Autowired
    private CouponTemplateRepository couponTemplateRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("주문 실패 시 롤백 테스트: ")
    @Nested
    class Rollback {

        @DisplayName("포인트 부족으로 실패하면, 재고가 원복된다.")
        @Test
        void rollsBackStock_whenPointInsufficient() {
            // arrange
            int initialStock = 10;
            Product product = productRepository.save(
                new Product(1L, "상품", 50000L, "설명", initialStock)
            );
            User user = userRepository.save(new User("테스터", 100L));

            // act
            assertThatThrownBy(() ->
                orderFacade.createOrder(
                    user.getId(),
                    List.of(new OrderFacade.OrderItemRequest(product.getId(), 1))
                )
            ).isInstanceOf(CoreException.class);

            // assert
            Product updatedProduct = productRepository.findById(product.getId()).orElseThrow();
            assertThat(updatedProduct.getStockQuantity().value()).isEqualTo(initialStock);
        }

        @DisplayName("포인트 부족으로 실패하면, 쿠폰 상태가 원복된다.")
        @Test
        void rollsBackCoupon_whenPointInsufficient() {
            // arrange
            CouponTemplate template = couponTemplateRepository.save(
                new CouponTemplate("1000원 할인", DiscountType.FIXED, 1000L, 0L, null, 30)
            );
            User user = userRepository.save(new User("테스터", 100L));
            UserCoupon coupon = userCouponRepository.save(
                new UserCoupon(user.getId(), template.getId(), 30)
            );
            Product product = productRepository.save(
                new Product(1L, "상품", 50000L, "설명", 100)
            );

            // act
            assertThatThrownBy(() ->
                orderFacade.createOrder(
                    user.getId(),
                    coupon.getId(),
                    List.of(new OrderFacade.OrderItemRequest(product.getId(), 1))
                )
            ).isInstanceOf(CoreException.class);

            // assert
            UserCoupon updatedCoupon = userCouponRepository.findById(coupon.getId()).orElseThrow();
            assertThat(updatedCoupon.getStatus()).isEqualTo(CouponStatus.AVAILABLE);
        }

        @DisplayName("재고 부족으로 실패하면, 주문이 저장되지 않는다.")
        @Test
        void doesNotSaveOrder_whenStockInsufficient() {
            // arrange
            Product product = productRepository.save(
                new Product(1L, "상품", 1000L, "설명", 0)
            );
            User user = userRepository.save(new User("테스터", 100_000L));

            // act
            assertThatThrownBy(() ->
                orderFacade.createOrder(
                    user.getId(),
                    List.of(new OrderFacade.OrderItemRequest(product.getId(), 1))
                )
            ).isInstanceOf(CoreException.class);

            // assert
            User updatedUser = userRepository.findById(user.getId()).orElseThrow();
            assertAll(
                () -> assertThat(updatedUser.getPoint()).as("포인트 원복").isEqualTo(100_000L)
            );
        }
    }
}
