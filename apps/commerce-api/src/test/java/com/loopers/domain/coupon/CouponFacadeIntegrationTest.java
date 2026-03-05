package com.loopers.domain.coupon;

import com.loopers.application.coupon.CouponAdminFacade;
import com.loopers.application.coupon.CouponFacade;
import com.loopers.application.coupon.UserCouponInfo;
import com.loopers.application.coupon.CouponTemplateInfo;
import com.loopers.application.coupon.CouponTemplateRegisterCommand;
import com.loopers.application.coupon.CouponTemplateUpdateCommand;
import com.loopers.infrastructure.coupon.UserCouponJpaRepository;
import com.loopers.infrastructure.coupon.CouponTemplateJpaRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
public class CouponFacadeIntegrationTest {

    private static final Long USER_ID = 1L;
    private static final Long OTHER_USER_ID = 2L;
    private static final Long NOT_EXISTED_TEMPLATE_ID = 999L;
    private static final LocalDateTime FUTURE_EXPIRED_AT = LocalDateTime.now().plusDays(30);
    private static final LocalDateTime PAST_EXPIRED_AT = LocalDateTime.now().minusDays(1);

    @Autowired
    private CouponFacade couponFacade;

    @Autowired
    private CouponAdminFacade couponAdminFacade;

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

    private CouponTemplate createSavedTemplate(LocalDateTime expiredAt) {
        return couponTemplateJpaRepository.save(
                new CouponTemplate("신규가입 10% 할인", CouponType.RATE, 10, null, expiredAt)
        );
    }

    @DisplayName("쿠폰 발급 시")
    @Nested
    class Issue {

        @DisplayName("정상적인 요청으로 쿠폰이 발급된다.")
        @Test
        void issuesCoupon_whenValidRequest() {
            // arrange
            CouponTemplate template = createSavedTemplate(FUTURE_EXPIRED_AT);

            // act
            UserCouponInfo result = couponFacade.issue(USER_ID, template.getId());

            // assert
            assertThat(result.id()).isPositive();
            assertThat(result.couponTemplateId()).isEqualTo(template.getId());
            assertThat(result.status()).isEqualTo(CouponStatus.AVAILABLE);
        }

        @DisplayName("존재하지 않는 쿠폰 템플릿에 발급 요청하면 NOT_FOUND 에러가 발생한다.")
        @Test
        void throwsNotFound_whenTemplateDoesNotExist() {
            // act
            CoreException result = assertThrows(CoreException.class,
                    () -> couponFacade.issue(USER_ID, NOT_EXISTED_TEMPLATE_ID));

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }

        @DisplayName("이미 발급받은 쿠폰에 재발급 요청하면 CONFLICT 에러가 발생한다. (BR-C03)")
        @Test
        void throwsConflict_whenAlreadyIssued() {
            // arrange
            CouponTemplate template = createSavedTemplate(FUTURE_EXPIRED_AT);
            couponFacade.issue(USER_ID, template.getId());

            // act
            CoreException result = assertThrows(CoreException.class,
                    () -> couponFacade.issue(USER_ID, template.getId()));

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.CONFLICT);
        }
    }

    @DisplayName("내 쿠폰 목록 조회 시")
    @Nested
    class FindMyIssuedCoupons {

        @DisplayName("자신의 쿠폰만 반환한다.")
        @Test
        void returnsOnlyOwnCoupons() {
            // arrange
            CouponTemplate template = createSavedTemplate(FUTURE_EXPIRED_AT);
            couponFacade.issue(USER_ID, template.getId());

            CouponTemplate otherTemplate = couponTemplateJpaRepository.save(
                    new CouponTemplate("타인용 쿠폰", CouponType.FIXED, 1000, null, FUTURE_EXPIRED_AT));
            couponFacade.issue(OTHER_USER_ID, otherTemplate.getId());

            // act
            List<UserCouponInfo> result = couponFacade.findMyIssuedCoupons(USER_ID);

            // assert
            assertThat(result).hasSize(1);
            assertThat(result.get(0).couponTemplateId()).isEqualTo(template.getId());
        }

        @DisplayName("만료된 쿠폰은 EXPIRED 상태로 반환된다. (BR-C04)")
        @Test
        void returnsExpiredStatus_forExpiredCoupon() {
            // arrange
            CouponTemplate expiredTemplate = createSavedTemplate(PAST_EXPIRED_AT);
            userCouponJpaRepository.save(new UserCoupon(expiredTemplate.getId(), USER_ID, expiredTemplate.getExpiredAt()));

            // act
            List<UserCouponInfo> result = couponFacade.findMyIssuedCoupons(USER_ID);

            // assert
            assertThat(result).hasSize(1);
            assertThat(result.get(0).status()).isEqualTo(CouponStatus.EXPIRED);
        }
    }

    @DisplayName("쿠폰 템플릿 등록 시")
    @Nested
    class RegisterTemplate {

        @DisplayName("정상적인 요청으로 쿠폰 템플릿이 등록된다.")
        @Test
        void registersTemplate_whenValidRequest() {
            // arrange
            CouponTemplateRegisterCommand command = new CouponTemplateRegisterCommand(
                    "신규가입 10% 할인", CouponType.RATE, 10, 5000, FUTURE_EXPIRED_AT
            );

            // act
            CouponTemplateInfo result = couponAdminFacade.register(command);

            // assert
            assertThat(result.id()).isPositive();
            assertThat(result.name()).isEqualTo("신규가입 10% 할인");
        }
    }

    @DisplayName("쿠폰 템플릿 수정 시")
    @Nested
    class UpdateTemplate {

        @DisplayName("존재하는 템플릿을 수정하면 수정된 정보가 반환된다.")
        @Test
        void updatesTemplate_whenTemplateExists() {
            // arrange
            CouponTemplate template = createSavedTemplate(FUTURE_EXPIRED_AT);
            CouponTemplateUpdateCommand command = new CouponTemplateUpdateCommand(
                    "수정된 쿠폰", CouponType.FIXED, 3000, 10000, FUTURE_EXPIRED_AT
            );

            // act
            CouponTemplateInfo result = couponAdminFacade.update(template.getId(), command);

            // assert
            assertThat(result.name()).isEqualTo("수정된 쿠폰");
            assertThat(result.type()).isEqualTo(CouponType.FIXED);
            assertThat(result.value()).isEqualTo(3000);
        }

        @DisplayName("존재하지 않는 템플릿을 수정하면 NOT_FOUND 에러가 발생한다.")
        @Test
        void throwsNotFound_whenTemplateDoesNotExist() {
            // arrange
            CouponTemplateUpdateCommand command = new CouponTemplateUpdateCommand(
                    "수정된 쿠폰", CouponType.FIXED, 3000, null, FUTURE_EXPIRED_AT
            );

            // act
            CoreException result = assertThrows(CoreException.class,
                    () -> couponAdminFacade.update(NOT_EXISTED_TEMPLATE_ID, command));

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }
    }

    @DisplayName("쿠폰 템플릿 삭제 시")
    @Nested
    class DeleteTemplate {

        @DisplayName("템플릿 삭제 시 발급 쿠폰도 함께 soft delete 된다. (BR-C05)")
        @Test
        void softDeletesTemplateAndRelatedIssues_whenDeleted() {
            // arrange
            CouponTemplate template = createSavedTemplate(FUTURE_EXPIRED_AT);
            userCouponJpaRepository.save(new UserCoupon(template.getId(), USER_ID, template.getExpiredAt()));

            // act
            couponAdminFacade.delete(template.getId());

            // assert
            CouponTemplate deletedTemplate = couponTemplateJpaRepository.findById(template.getId()).orElseThrow();
            long activeIssueCount = userCouponJpaRepository
                    .findAllByCouponTemplateIdAndDeletedAtIsNull(template.getId(), PageRequest.of(0, 1))
                    .getTotalElements();
            assertThat(deletedTemplate.getDeletedAt()).isNotNull();
            assertThat(activeIssueCount).isZero();
        }

        @DisplayName("존재하지 않는 템플릿을 삭제하면 NOT_FOUND 에러가 발생한다.")
        @Test
        void throwsNotFound_whenTemplateDoesNotExist() {
            // act
            CoreException result = assertThrows(CoreException.class,
                    () -> couponAdminFacade.delete(NOT_EXISTED_TEMPLATE_ID));

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }
    }

    @DisplayName("발급 내역 조회 시")
    @Nested
    class FindIssuesByTemplateId {

        @DisplayName("특정 쿠폰 템플릿의 전체 발급 내역을 페이지 단위로 반환한다.")
        @Test
        void returnsIssuesByTemplate_withPaging() {
            // arrange
            CouponTemplate template = createSavedTemplate(FUTURE_EXPIRED_AT);
            userCouponJpaRepository.save(new UserCoupon(template.getId(), USER_ID, template.getExpiredAt()));
            userCouponJpaRepository.save(new UserCoupon(template.getId(), OTHER_USER_ID, template.getExpiredAt()));

            // act
            Page<UserCouponInfo> result = couponAdminFacade.findIssuesByTemplateId(template.getId(), PageRequest.of(0, 20));

            // assert
            assertThat(result.getTotalElements()).isEqualTo(2);
        }
    }

}
