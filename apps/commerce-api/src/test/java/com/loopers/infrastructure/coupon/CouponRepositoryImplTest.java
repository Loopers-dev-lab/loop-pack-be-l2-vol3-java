package com.loopers.infrastructure.coupon;

import com.loopers.domain.coupon.CouponModel;
import com.loopers.domain.coupon.CouponRepository;
import com.loopers.support.enums.DiscountType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import({CouponRepositoryImpl.class, UserCouponRepositoryImpl.class})
@ActiveProfiles("test")
@DisplayName("CouponRepositoryImpl 테스트")
class CouponRepositoryImplTest {

    @Autowired
    CouponRepository couponRepository;

    @Autowired
    CouponJpaRepository couponJpaRepository;

    private CouponModel saveTestCoupon(String name) {
        CouponModel coupon = CouponModel.create(name, DiscountType.FIXED, BigDecimal.valueOf(1000), null,
                LocalDateTime.now().plusDays(30));
        return couponRepository.save(coupon);
    }

    @Test
    @DisplayName("쿠폰을 저장하고 ID로 조회할 수 있다")
    void save_ShouldPersistCoupon() {
        CouponModel saved = saveTestCoupon("테스트쿠폰");
        assertThat(saved.getCouponId()).isNotNull();

        Optional<CouponModel> found = couponRepository.findById(saved.getCouponId());
        assertThat(found).isPresent();
        assertThat(found.get().getName()).isEqualTo("테스트쿠폰");
    }

    @Test
    @DisplayName("findByIdWithLock으로 쿠폰을 조회할 수 있다")
    void findByIdWithLock_ShouldReturnCoupon() {
        CouponModel saved = saveTestCoupon("락쿠폰");
        Optional<CouponModel> found = couponRepository.findByIdWithLock(saved.getCouponId());
        assertThat(found).isPresent();
        assertThat(found.get().getCouponId()).isEqualTo(saved.getCouponId());
    }

    @Test
    @DisplayName("findAll은 삭제된 쿠폰을 포함하여 반환한다")
    void findAll_ShouldIncludeDeletedCoupons() {
        saveTestCoupon("활성쿠폰");
        CouponModel deleted = saveTestCoupon("삭제쿠폰");
        deleted.softDelete();
        couponRepository.save(deleted);

        List<CouponModel> all = couponRepository.findAll();
        assertThat(all).hasSizeGreaterThanOrEqualTo(2);
        assertThat(all.stream().anyMatch(CouponModel::isDeleted)).isTrue();
    }
}
