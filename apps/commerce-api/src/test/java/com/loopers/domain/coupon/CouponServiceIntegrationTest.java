package com.loopers.domain.coupon;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import com.loopers.testcontainers.MySqlTestContainersConfig;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.time.ZonedDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 쿠폰 도메인 서비스 통합 테스트. 실제 DB(Testcontainers) 사용.
 */
@SpringBootTest
@Import(MySqlTestContainersConfig.class)
class CouponServiceIntegrationTest {

    @Autowired
    private CouponService couponService;
    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("issue 시")
    @Nested
    class Issue {

        @Test
        void issue_withValidTemplate_shouldPersistAndReturn() {
            CouponTemplateModel template = couponService.persistTemplate(
                    CouponTemplateModel.create("통합테스트 쿠폰", CouponType.FIXED, 1000,
                            BigDecimal.valueOf(5000), ZonedDateTime.now().plusDays(30)));

            IssuedCouponModel issued = couponService.issue(1L, template.getId());

            assertThat(issued.getId()).isNotNull();
            assertThat(issued.getUserId()).isEqualTo(1L);
            assertThat(issued.getCouponId()).isEqualTo(template.getId());
            assertThat(issued.getStatus()).isEqualTo(IssuedCouponStatus.AVAILABLE);
        }

        @Test
        void issue_withNonExistentTemplate_shouldThrowNotFound() {
            CoreException ex = assertThrows(CoreException.class, () -> couponService.issue(1L, 999_999L));
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }

        @Test
        void issue_whenTemplateExpired_shouldThrowBadRequest() {
            CouponTemplateModel template = couponService.persistTemplate(
                    CouponTemplateModel.create("만료쿠폰", CouponType.FIXED, 500,
                            null, ZonedDateTime.now().minusDays(1)));

            CoreException ex = assertThrows(CoreException.class, () -> couponService.issue(1L, template.getId()));
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @Test
        void issue_whenSameUserAlreadyIssued_shouldThrowConflict() {
            CouponTemplateModel template = couponService.persistTemplate(
                    CouponTemplateModel.create("중복검증", CouponType.FIXED, 100,
                            null, ZonedDateTime.now().plusDays(30)));
            couponService.issue(1L, template.getId());

            CoreException ex = assertThrows(CoreException.class, () -> couponService.issue(1L, template.getId()));

            assertThat(ex.getErrorType()).isEqualTo(ErrorType.CONFLICT);
        }

        @Test
        void issue_whenMaxIssueCountReached_shouldThrowBadRequest() {
            CouponTemplateModel template = couponService.persistTemplate(
                    CouponTemplateModel.create("선착순1장", CouponType.FIXED, 100,
                            null, ZonedDateTime.now().plusDays(30), 1));
            couponService.issue(1L, template.getId());

            CoreException ex = assertThrows(CoreException.class, () -> couponService.issue(2L, template.getId()));

            assertThat(ex.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @DisplayName("findByUserIdAsProjection 시")
    @Nested
    class FindByUserIdAsProjection {

        @Test
        void findByUserIdAsProjection_afterIssue_shouldReturnPage() {
            CouponTemplateModel template = couponService.persistTemplate(
                    CouponTemplateModel.create("프로젝션테스트", CouponType.RATE, 10,
                            null, ZonedDateTime.now().plusDays(7)));
            couponService.issue(100L, template.getId());

            var page = couponService.findByUserIdAsProjection(100L, PageRequest.of(0, 10));

            assertThat(page.getContent()).hasSize(1);
            assertThat(page.getContent().get(0).getCouponId()).isEqualTo(template.getId());
            assertThat(page.getContent().get(0).getStatus()).isEqualTo(IssuedCouponStatus.AVAILABLE);
        }
    }

    @DisplayName("findTemplatesNotDeletedAsProjection 시")
    @Nested
    class FindTemplatesNotDeletedAsProjection {

        @Test
        void findTemplatesNotDeletedAsProjection_shouldReturnOnlyNotDeleted() {
            couponService.persistTemplate(
                    CouponTemplateModel.create("목록1", CouponType.FIXED, 100, null, ZonedDateTime.now().plusDays(1)));

            var page = couponService.findTemplatesNotDeletedAsProjection(PageRequest.of(0, 10));

            assertThat(page.getContent()).isNotEmpty();
            assertThat(page.getContent().get(0).getName()).isEqualTo("목록1");
            assertThat(page.getContent().get(0).getType()).isEqualTo(CouponType.FIXED);
        }
    }

    @DisplayName("findTemplateByIdAndNotDeletedAsProjection 시")
    @Nested
    class FindTemplateByIdAndNotDeletedAsProjection {

        @Test
        void findTemplateByIdAndNotDeletedAsProjection_whenExists_shouldReturnPresent() {
            CouponTemplateModel template = couponService.persistTemplate(
                    CouponTemplateModel.create("단건프로젝션", CouponType.RATE, 20,
                            BigDecimal.valueOf(10000), ZonedDateTime.now().plusDays(5)));

            var opt = couponService.findTemplateByIdAndNotDeletedAsProjection(template.getId());

            assertThat(opt).isPresent();
            assertThat(opt.get().getName()).isEqualTo("단건프로젝션");
            assertThat(opt.get().getValue()).isEqualTo(20);
        }

        @Test
        void findTemplateByIdAndNotDeletedAsProjection_whenNotExists_shouldReturnEmpty() {
            var opt = couponService.findTemplateByIdAndNotDeletedAsProjection(999_999L);
            assertThat(opt).isEmpty();
        }
    }

    @DisplayName("validateAndUse 시")
    @Nested
    class ValidateAndUse {

        @Test
        void validateAndUse_withValidIssuedCoupon_shouldMarkUsedAndReturnDiscount() {
            CouponTemplateModel template = couponService.persistTemplate(
                    CouponTemplateModel.create("사용테스트", CouponType.FIXED, 3000,
                            BigDecimal.valueOf(10000), ZonedDateTime.now().plusDays(30)));
            IssuedCouponModel issued = couponService.issue(200L, template.getId());

            CouponDiscount discount = couponService.validateAndUse(
                    issued.getId(), 200L, BigDecimal.valueOf(50000));

            assertThat(discount.beforeAmount()).isEqualByComparingTo(BigDecimal.valueOf(50000));
            assertThat(discount.discountAmount()).isEqualByComparingTo(BigDecimal.valueOf(3000));
            assertThat(discount.afterAmount()).isEqualByComparingTo(BigDecimal.valueOf(47000));

            var after = couponService.findByUserId(200L, PageRequest.of(0, 1)).getContent().get(0);
            assertThat(after.getStatus()).isEqualTo(IssuedCouponStatus.USED);
        }

        @Test
        void validateAndUse_whenMinOrderAmountNotMet_shouldThrowBadRequest() {
            CouponTemplateModel template = couponService.persistTemplate(
                    CouponTemplateModel.create("최소주문", CouponType.FIXED, 1000,
                            BigDecimal.valueOf(50000), ZonedDateTime.now().plusDays(30)));
            IssuedCouponModel issued = couponService.issue(300L, template.getId());

            CoreException ex = assertThrows(CoreException.class, () ->
                    couponService.validateAndUse(issued.getId(), 300L, BigDecimal.valueOf(10000)));
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @Test
        void validateAndUse_whenWrongUser_shouldThrowNotFound() {
            CouponTemplateModel template = couponService.persistTemplate(
                    CouponTemplateModel.create("타인쿠폰", CouponType.FIXED, 500, null, ZonedDateTime.now().plusDays(7)));
            IssuedCouponModel issued = couponService.issue(400L, template.getId());

            CoreException ex = assertThrows(CoreException.class, () ->
                    couponService.validateAndUse(issued.getId(), 999L, BigDecimal.valueOf(10000)));
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }
    }
}
