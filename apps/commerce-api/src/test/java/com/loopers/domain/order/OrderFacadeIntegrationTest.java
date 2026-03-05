package com.loopers.domain.order;

import com.loopers.application.order.OrderCreateCommand;
import com.loopers.application.order.OrderFacade;
import com.loopers.application.order.OrderInfo;
import com.loopers.domain.brand.Brand;
import com.loopers.domain.coupon.CouponTemplate;
import com.loopers.domain.coupon.CouponType;
import com.loopers.domain.coupon.UserCoupon;
import com.loopers.domain.product.Money;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.Stock;
import com.loopers.infrastructure.brand.BrandJpaRepository;
import com.loopers.infrastructure.coupon.CouponTemplateJpaRepository;
import com.loopers.infrastructure.coupon.UserCouponJpaRepository;
import com.loopers.infrastructure.product.ProductJpaRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
public class OrderFacadeIntegrationTest {

    private static final Long USER_ID = 1L;
    private static final Long OTHER_USER_ID = 2L;
    private static final Long NOT_EXISTED_COUPON_ID = 999L;
    private static final Money VALID_PRICE = new Money(10000);
    private static final int INITIAL_STOCK = 10;
    private static final LocalDateTime FUTURE_EXPIRED_AT = LocalDateTime.now().plusDays(30);
    private static final LocalDateTime PAST_EXPIRED_AT = LocalDateTime.now().minusDays(1);

    @Autowired
    private OrderFacade orderFacade;

    @Autowired
    private BrandJpaRepository brandJpaRepository;

    @Autowired
    private ProductJpaRepository productJpaRepository;

    @Autowired
    private CouponTemplateJpaRepository couponTemplateJpaRepository;

    @Autowired
    private UserCouponJpaRepository userCouponJpaRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    private Brand savedBrand() {
        return brandJpaRepository.save(new Brand("나이키"));
    }

    private Product savedProduct(Long brandId) {
        return productJpaRepository.save(
                new Product(brandId, "나이키 에어맥스", VALID_PRICE, new Stock(INITIAL_STOCK)));
    }

    private CouponTemplate savedFixedTemplate(int discountValue, Integer minOrderAmount, LocalDateTime expiredAt) {
        return couponTemplateJpaRepository.save(
                new CouponTemplate("정액 할인 쿠폰", CouponType.FIXED, discountValue, minOrderAmount, expiredAt));
    }

    private UserCoupon savedCoupon(Long templateId, Long userId, LocalDateTime expiredAt) {
        return userCouponJpaRepository.save(new UserCoupon(templateId, userId, expiredAt));
    }

    private OrderCreateCommand command(Long productId, int quantity, Long userCouponId) {
        return new OrderCreateCommand(
                List.of(new OrderCreateCommand.Item(productId, quantity)),
                userCouponId
        );
    }

    @DisplayName("쿠폰을 적용하여 주문 생성 시")
    @Nested
    class CreateOrderWithCoupon {

        @DisplayName("유효한 쿠폰을 적용하면 할인 금액이 반영되고, 쿠폰이 사용 처리되며, 재고가 차감된다.")
        @Test
        void appliesDiscountMarksCouponUsedAndDecreasesStock_whenValidCoupon() {
            // arrange
            Brand brand = savedBrand();
            Product product = savedProduct(brand.getId());
            CouponTemplate template = savedFixedTemplate(1000, null, FUTURE_EXPIRED_AT);
            UserCoupon coupon = savedCoupon(template.getId(), USER_ID, FUTURE_EXPIRED_AT);

            // act
            OrderInfo result = orderFacade.create(USER_ID, command(product.getId(), 1, coupon.getId()));

            // assert
            assertThat(result.originalAmount()).isEqualTo(VALID_PRICE.getAmount());
            assertThat(result.discountAmount()).isEqualTo(1000);
            assertThat(result.finalAmount()).isEqualTo(VALID_PRICE.getAmount() - 1000);

            UserCoupon usedCoupon = userCouponJpaRepository.findById(coupon.getId()).orElseThrow();
            assertThat(usedCoupon.getUsedAt()).isNotNull();

            Product updated = productJpaRepository.findById(product.getId()).orElseThrow();
            assertThat(updated.getStock().getQuantity()).isEqualTo(INITIAL_STOCK - 1);
        }

        @DisplayName("존재하지 않는 쿠폰으로 주문하면 NOT_FOUND 에러가 발생한다.")
        @Test
        void throwsNotFound_whenCouponDoesNotExist() {
            // arrange
            Brand brand = savedBrand();
            Product product = savedProduct(brand.getId());

            // act
            CoreException result = assertThrows(CoreException.class,
                    () -> orderFacade.create(USER_ID, command(product.getId(), 1, NOT_EXISTED_COUPON_ID)));

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }

        @DisplayName("만료된 쿠폰으로 주문하면 BAD_REQUEST 에러가 발생한다.")
        @Test
        void throwsBadRequest_whenCouponIsExpired() {
            // arrange
            Brand brand = savedBrand();
            Product product = savedProduct(brand.getId());
            CouponTemplate template = savedFixedTemplate(1000, null, PAST_EXPIRED_AT);
            UserCoupon expiredCoupon = savedCoupon(template.getId(), USER_ID, PAST_EXPIRED_AT);

            // act
            CoreException result = assertThrows(CoreException.class,
                    () -> orderFacade.create(USER_ID, command(product.getId(), 1, expiredCoupon.getId())));

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("이미 사용된 쿠폰으로 주문하면 BAD_REQUEST 에러가 발생하고 재고는 추가 차감되지 않는다.")
        @Test
        void throwsBadRequestAndKeepsStock_whenCouponAlreadyUsed() {
            // arrange
            Brand brand = savedBrand();
            Product product = savedProduct(brand.getId());
            CouponTemplate template = savedFixedTemplate(1000, null, FUTURE_EXPIRED_AT);
            UserCoupon coupon = savedCoupon(template.getId(), USER_ID, FUTURE_EXPIRED_AT);

            // 쿠폰 첫 사용 (재고 10 → 9)
            orderFacade.create(USER_ID, command(product.getId(), 1, coupon.getId()));

            // act: 동일 쿠폰으로 재사용 시도
            CoreException result = assertThrows(CoreException.class,
                    () -> orderFacade.create(USER_ID, command(product.getId(), 1, coupon.getId())));

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
            // 재고가 9에서 더 차감되지 않았음을 확인
            Product unchanged = productJpaRepository.findById(product.getId()).orElseThrow();
            assertThat(unchanged.getStock().getQuantity()).isEqualTo(INITIAL_STOCK - 1);
        }

        @DisplayName("타인의 쿠폰으로 주문하면 NOT_FOUND 에러가 발생한다.")
        @Test
        void throwsNotFound_whenCouponBelongsToOtherUser() {
            // arrange
            Brand brand = savedBrand();
            Product product = savedProduct(brand.getId());
            CouponTemplate template = savedFixedTemplate(1000, null, FUTURE_EXPIRED_AT);
            UserCoupon otherUserCoupon = savedCoupon(template.getId(), OTHER_USER_ID, FUTURE_EXPIRED_AT);

            // act: USER_ID로 OTHER_USER_ID의 쿠폰 사용 시도
            CoreException result = assertThrows(CoreException.class,
                    () -> orderFacade.create(USER_ID, command(product.getId(), 1, otherUserCoupon.getId())));

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }

        @DisplayName("최소 주문 금액 미충족 쿠폰으로 주문하면 BAD_REQUEST 에러가 발생한다.")
        @Test
        void throwsBadRequest_whenOrderAmountBelowMinimum() {
            // arrange
            Brand brand = savedBrand();
            Product product = savedProduct(brand.getId()); // 10,000원 상품 1개 = 10,000원
            CouponTemplate template = savedFixedTemplate(1000, 20000, FUTURE_EXPIRED_AT); // 최소 20,000원 필요
            UserCoupon coupon = savedCoupon(template.getId(), USER_ID, FUTURE_EXPIRED_AT);

            // act: 10,000원 주문 (최소 주문 금액 20,000원 미충족)
            CoreException result = assertThrows(CoreException.class,
                    () -> orderFacade.create(USER_ID, command(product.getId(), 1, coupon.getId())));

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("쿠폰이 유효하지 않으면 재고가 차감되지 않는다. (트랜잭션 롤백)")
        @Test
        void doesNotDecreaseStock_whenCouponFails() {
            // arrange
            Brand brand = savedBrand();
            Product product = savedProduct(brand.getId()); // 재고 10
            CouponTemplate template = savedFixedTemplate(1000, null, PAST_EXPIRED_AT);
            UserCoupon expiredCoupon = savedCoupon(template.getId(), USER_ID, PAST_EXPIRED_AT);

            // act: 만료 쿠폰으로 주문 → ③ 쿠폰 적용 단계에서 실패 (④ 재고 차감 미실행)
            assertThrows(CoreException.class,
                    () -> orderFacade.create(USER_ID, command(product.getId(), 1, expiredCoupon.getId())));

            // assert: 트랜잭션 롤백으로 재고 원복 확인
            Product unchanged = productJpaRepository.findById(product.getId()).orElseThrow();
            assertThat(unchanged.getStock().getQuantity()).isEqualTo(INITIAL_STOCK);
        }
    }
}
