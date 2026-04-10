package com.loopers.domain.coupon;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;

import com.loopers.domain.coupon.dto.CouponCommand;
import com.loopers.support.error.CoreException;
import java.time.ZonedDateTime;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("CouponService 단위 테스트")
class CouponServiceTest {

    private CouponService couponService;
    private FakeCouponRepository couponRepository;
    private FakeOwnedCouponRepository ownedCouponRepository;

    @BeforeEach
    void setUp() {
        couponRepository = new FakeCouponRepository();
        ownedCouponRepository = new FakeOwnedCouponRepository();
        couponService = new CouponService(couponRepository, ownedCouponRepository);
    }

    @DisplayName("쿠폰을 등록할 때, ")
    @Nested
    class Register {

        @DisplayName("유효한 커맨드가 주어지면 저장되고 반환된다")
        @Test
        void register_whenValidCommand() {
            // arrange
            var command = new CouponCommand.Create(
                    "신규가입 할인", CouponDiscountType.FIXED, 5000L,
                    null, 1000, ZonedDateTime.now().plusDays(30));

            // act
            CouponModel result = couponService.register(command);

            // assert
            assertAll(
                    () -> assertThat(result.getId()).isNotNull(),
                    () -> assertThat(result.getName()).isEqualTo("신규가입 할인"),
                    () -> assertThat(result.getDiscountType()).isEqualTo(CouponDiscountType.FIXED),
                    () -> assertThat(result.getDiscountValue()).isEqualTo(5000L));
        }
    }

    @DisplayName("쿠폰을 단건 조회할 때, ")
    @Nested
    class GetById {

        @DisplayName("존재하지 않으면 예외가 발생한다")
        @Test
        void getById_whenNotFound() {
            // act & assert
            assertThatThrownBy(() -> couponService.getById(999L))
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> assertThat(((CoreException) e).getErrorCode())
                            .isEqualTo(CouponErrorCode.NOT_FOUND));
        }
    }

    @DisplayName("쿠폰 목록을 조회할 때, ")
    @Nested
    class GetAll {

        @DisplayName("페이지네이션으로 조회된다")
        @Test
        void getAll_withPagination() {
            // arrange
            for (int i = 0; i < 3; i++) {
                couponRepository.save(CouponModel.create(
                        "쿠폰" + i, CouponDiscountType.FIXED, 1000L,
                        null, 100, ZonedDateTime.now().plusDays(30)));
            }

            // act
            Page<CouponModel> result = couponService.getAll(PageRequest.of(0, 2));

            // assert
            assertAll(
                    () -> assertThat(result.getContent()).hasSize(2),
                    () -> assertThat(result.getTotalElements()).isEqualTo(3));
        }
    }

    @DisplayName("쿠폰을 수정할 때, ")
    @Nested
    class Update {

        @DisplayName("name과 expiredAt이 변경된다")
        @Test
        void update_nameAndExpiredAt() {
            // arrange
            CouponModel coupon = couponRepository.save(CouponModel.create(
                    "기존 쿠폰", CouponDiscountType.FIXED, 5000L,
                    null, 1000, ZonedDateTime.now().plusDays(30)));
            ZonedDateTime newExpiredAt = ZonedDateTime.now().plusDays(60);

            // act
            couponService.update(coupon.getId(),
                    new CouponCommand.Update("수정된 쿠폰", newExpiredAt));

            // assert
            CouponModel updated = couponRepository.findById(coupon.getId()).orElseThrow();
            assertAll(
                    () -> assertThat(updated.getName()).isEqualTo("수정된 쿠폰"),
                    () -> assertThat(updated.getExpiredAt()).isEqualTo(newExpiredAt));
        }
    }

    @DisplayName("쿠폰을 삭제할 때, ")
    @Nested
    class Delete {

        @DisplayName("soft delete 처리된다")
        @Test
        void delete_softDelete() {
            // arrange
            CouponModel coupon = couponRepository.save(CouponModel.create(
                    "삭제 대상", CouponDiscountType.FIXED, 5000L,
                    null, 1000, ZonedDateTime.now().plusDays(30)));

            // act
            couponService.delete(coupon.getId());

            // assert
            assertThat(couponRepository.findById(coupon.getId())).isEmpty();
        }
    }

    @DisplayName("쿠폰을 발급할 때, ")
    @Nested
    class Issue {

        @DisplayName("성공하면 OwnedCouponModel이 생성되고 issuedQuantity가 증가한다")
        @Test
        void issue_success() {
            // arrange
            CouponModel coupon = couponRepository.save(CouponModel.create(
                    "발급 쿠폰", CouponDiscountType.FIXED, 5000L,
                    null, 1000, ZonedDateTime.now().plusDays(30)));

            // act
            OwnedCouponModel result = couponService.issue(coupon.getId(), 100L);

            // assert
            assertAll(
                    () -> assertThat(result.getCouponId()).isEqualTo(coupon.getId()),
                    () -> assertThat(result.getUserId()).isEqualTo(100L),
                    () -> assertThat(result.isAvailable()).isTrue(),
                    () -> assertThat(coupon.getIssuedQuantity()).isEqualTo(1));
        }

        @DisplayName("이미 발급받은 사용자가 중복 발급하면 예외가 발생한다")
        @Test
        void issue_whenAlreadyIssued() {
            // arrange
            CouponModel coupon = couponRepository.save(CouponModel.create(
                    "발급 쿠폰", CouponDiscountType.FIXED, 5000L,
                    null, 1000, ZonedDateTime.now().plusDays(30)));
            couponService.issue(coupon.getId(), 100L);

            // act & assert
            assertThatThrownBy(() -> couponService.issue(coupon.getId(), 100L))
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> assertThat(((CoreException) e).getErrorCode())
                            .isEqualTo(CouponErrorCode.ALREADY_ISSUED));
        }
    }

    @DisplayName("쿠폰을 사용하고 할인을 계산할 때, ")
    @Nested
    class UseAndCalculateDiscount {

        @DisplayName("성공하면 CAS Update로 orderId가 기록되고 할인 금액이 반환된다")
        @Test
        void useAndCalculateDiscount_success() {
            // arrange
            CouponModel coupon = couponRepository.save(CouponModel.create(
                    "5000원 할인", CouponDiscountType.FIXED, 5000L,
                    null, 1000, ZonedDateTime.now().plusDays(30)));
            OwnedCouponModel owned = ownedCouponRepository.save(
                    OwnedCouponModel.create(coupon, 100L));

            // act
            long discount = couponService.useAndCalculateDiscount(
                    owned.getId(), 100L, 1L, 50000L);

            // assert
            assertAll(
                    () -> assertThat(discount).isEqualTo(5000L),
                    () -> assertThat(owned.isUsed()).isTrue(),
                    () -> assertThat(owned.getOrderId()).isEqualTo(1L));
        }
    }

    @DisplayName("쿠폰을 복원할 때, ")
    @Nested
    class Restore {

        @DisplayName("orderId로 복원하면 orderId/usedAt이 초기화된다")
        @Test
        void restoreByOrderId_success() {
            // arrange
            CouponModel coupon = couponRepository.save(CouponModel.create(
                    "5000원 할인", CouponDiscountType.FIXED, 5000L,
                    null, 1000, ZonedDateTime.now().plusDays(30)));
            OwnedCouponModel owned = ownedCouponRepository.save(
                    OwnedCouponModel.create(coupon, 100L));
            owned.use(100L, 1L);

            // act
            couponService.restoreByOrderId(1L);

            // assert
            assertAll(
                    () -> assertThat(owned.isAvailable()).isTrue(),
                    () -> assertThat(owned.getUsedAt()).isNull(),
                    () -> assertThat(owned.getOrderId()).isNull());
        }

        @DisplayName("해당 orderId의 쿠폰이 없으면 아무 일도 일어나지 않는다")
        @Test
        void restoreByOrderId_whenNoCoupon_noOp() {
            // act & assert (예외 없이 정상 종료)
            couponService.restoreByOrderId(999L);
        }
    }

    @DisplayName("내 쿠폰 목록을 조회할 때, ")
    @Nested
    class GetMyOwnedCoupons {

        @DisplayName("userId로 보유 쿠폰이 조회된다")
        @Test
        void getMyOwnedCoupons_byUserId() {
            // arrange
            CouponModel coupon = couponRepository.save(CouponModel.create(
                    "할인 쿠폰", CouponDiscountType.FIXED, 5000L,
                    null, 1000, ZonedDateTime.now().plusDays(30)));
            ownedCouponRepository.save(OwnedCouponModel.create(coupon, 100L));
            ownedCouponRepository.save(OwnedCouponModel.create(coupon, 200L));

            // act
            var result = couponService.getMyOwnedCoupons(100L);

            // assert
            assertAll(
                    () -> assertThat(result).hasSize(1),
                    () -> assertThat(result.get(0).getUserId()).isEqualTo(100L));
        }
    }

    @DisplayName("쿠폰 발급 내역을 조회할 때, ")
    @Nested
    class GetIssuedCoupons {

        @DisplayName("couponId로 페이지네이션 조회된다")
        @Test
        void getIssuedCoupons_byCouponId() {
            // arrange
            CouponModel coupon = couponRepository.save(CouponModel.create(
                    "할인 쿠폰", CouponDiscountType.FIXED, 5000L,
                    null, 1000, ZonedDateTime.now().plusDays(30)));
            for (long i = 1; i <= 3; i++) {
                ownedCouponRepository.save(OwnedCouponModel.create(coupon, i));
            }

            // act
            Page<OwnedCouponModel> result = couponService.getIssuedCoupons(
                    coupon.getId(), PageRequest.of(0, 2));

            // assert
            assertAll(
                    () -> assertThat(result.getContent()).hasSize(2),
                    () -> assertThat(result.getTotalElements()).isEqualTo(3));
        }
    }
}
