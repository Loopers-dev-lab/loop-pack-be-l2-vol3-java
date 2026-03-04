package com.loopers.domain.coupon;

import com.loopers.domain.coupon.model.CouponCommand;
import com.loopers.domain.coupon.model.CouponTemplate;
import com.loopers.domain.coupon.model.UserCoupon;
import com.loopers.domain.coupon.model.UserCouponItem;
import com.loopers.domain.coupon.service.CouponService;
import com.loopers.infrastructure.coupon.repository.CouponTemplateJpaRepository;
import com.loopers.infrastructure.coupon.repository.UserCouponJpaRepository;
import com.loopers.support.CouponEnums;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import com.loopers.testcontainers.MySqlTestContainersConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import jakarta.persistence.EntityManager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
@Import(MySqlTestContainersConfig.class)
@DisplayName("CouponService 통합 테스트")
class CouponServiceIntegrationTest {

    @Autowired
    private CouponService couponService;

    @Autowired
    private CouponTemplateJpaRepository couponTemplateJpaRepository;

    @Autowired
    private UserCouponJpaRepository userCouponJpaRepository;

    @Autowired
    private EntityManager entityManager;

    @BeforeEach
    void setUp() {
        userCouponJpaRepository.deleteAll();
        couponTemplateJpaRepository.deleteAll();
    }

    private CouponTemplate createTemplate() {
        CouponCommand.CreateTemplate command = new CouponCommand.CreateTemplate(
                "테스트 쿠폰", CouponEnums.Type.FIXED, 1000, 10000,
                LocalDateTime.now().plusDays(30));
        return couponService.createTemplate(command);
    }

    @Nested
    @DisplayName("전체 플로우")
    class FullFlow {

        @Test
        @DisplayName("템플릿 생성 → 발급 → 조회 → 사용 처리가 정상 동작한다")
        void success() {
            // 1. 템플릿 생성
            CouponTemplate template = createTemplate();
            assertThat(template.getId()).isNotNull();
            assertThat(template.getName().value()).isEqualTo("테스트 쿠폰");

            // 2. 쿠폰 발급
            UserCoupon userCoupon = couponService.issueCoupon(template.getId(), 1L);
            assertThat(userCoupon.getStatus()).isEqualTo(CouponEnums.Status.AVAILABLE);

            // 3. 내 쿠폰 목록 조회 (템플릿 정보 포함)
            Page<UserCouponItem> myCoupons = couponService.getUserCouponsWithTemplate(1L, PageRequest.of(0, 20));
            assertThat(myCoupons.getTotalElements()).isEqualTo(1);
            UserCouponItem item = myCoupons.getContent().get(0);
            assertThat(item.couponName()).isEqualTo("테스트 쿠폰");
            assertThat(item.type()).isEqualTo(CouponEnums.Type.FIXED);
            assertThat(item.status()).isEqualTo(CouponEnums.Status.AVAILABLE);

            // 4. 쿠폰 사용
            CouponTemplate usedTemplate = couponService.useUserCoupon(userCoupon.getId(), 1L, 50000);
            assertThat(usedTemplate.calculateDiscount(50000)).isEqualTo(1000);

            // 5. 재사용 시도 → 실패
            assertThatThrownBy(() -> couponService.useUserCoupon(userCoupon.getId(), 1L, 50000))
                    .isInstanceOf(CoreException.class);
        }
    }

    @Nested
    @DisplayName("중복 발급 방지")
    class DuplicateIssue {

        @Test
        @DisplayName("동일 사용자가 같은 쿠폰을 중복 발급받으면 CONFLICT 예외가 발생한다")
        void failWhenDuplicate() {
            CouponTemplate template = createTemplate();
            couponService.issueCoupon(template.getId(), 1L);

            assertThatThrownBy(() -> couponService.issueCoupon(template.getId(), 1L))
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.CONFLICT));
        }
    }

    @Nested
    @DisplayName("템플릿 CRUD")
    class TemplateCrud {

        @Test
        @DisplayName("생성, 조회, 수정, 삭제가 정상 동작한다")
        void crudFlow() {
            // 생성
            CouponTemplate template = createTemplate();

            // 조회
            CouponTemplate found = couponService.getTemplate(template.getId());
            assertThat(found.getName().value()).isEqualTo("테스트 쿠폰");

            // 목록 조회
            Page<CouponTemplate> list = couponService.getTemplates(PageRequest.of(0, 20));
            assertThat(list.getTotalElements()).isEqualTo(1);

            // 수정
            CouponCommand.UpdateTemplate updateCommand = new CouponCommand.UpdateTemplate(
                    "수정된 쿠폰", CouponEnums.Type.RATE, 15, 5000, LocalDateTime.now().plusDays(60));
            CouponTemplate updated = couponService.updateTemplate(template.getId(), updateCommand);
            assertThat(updated.getName().value()).isEqualTo("수정된 쿠폰");

            // 삭제 (Soft Delete)
            couponService.deleteTemplate(template.getId());
            entityManager.flush();
            entityManager.clear();

            // 삭제 후 조회 시 NOT_FOUND
            assertThatThrownBy(() -> couponService.getTemplate(template.getId()))
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.NOT_FOUND));
        }
    }
}
