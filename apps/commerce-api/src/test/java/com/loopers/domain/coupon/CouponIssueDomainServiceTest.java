package com.loopers.domain.coupon;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.time.ZonedDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CouponIssueDomainServiceTest {

    private CouponIssueDomainService couponIssueService;

    @BeforeEach
    void setUp() {
        couponIssueService = new CouponIssueDomainService(new FakeCouponIssueRepository());
    }

    private Coupon createValidCoupon(Long id) {
        Coupon coupon = new Coupon("할인쿠폰", CouponType.FIXED, 5000, 0, ZonedDateTime.now().plusDays(30));
        setId(coupon, id);
        return coupon;
    }

    private Coupon createExpiredCoupon(Long id) {
        Coupon coupon = new Coupon("만료쿠폰", CouponType.FIXED, 5000, 0, ZonedDateTime.now().minusDays(1));
        setId(coupon, id);
        return coupon;
    }

    private Coupon createDeletedCoupon(Long id) {
        Coupon coupon = createValidCoupon(id);
        coupon.delete();
        return coupon;
    }

    private void setId(Object entity, long id) {
        try {
            Field idField = entity.getClass().getSuperclass().getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(entity, id);
        } catch (Exception e) {
            throw new RuntimeException("Failed to set id", e);
        }
    }

    @DisplayName("쿠폰을 발급할 때, ")
    @Nested
    class Issue {

        @DisplayName("올바른 요청이면, 쿠폰이 발급된다.")
        @Test
        void issuesCoupon_whenValidRequest() {
            Coupon coupon = createValidCoupon(1L);

            CouponIssue issue = couponIssueService.issue(coupon, 1L);

            assertAll(
                () -> assertThat(issue.getId()).isNotNull(),
                () -> assertThat(issue.getCouponId()).isEqualTo(1L),
                () -> assertThat(issue.getUserId()).isEqualTo(1L),
                () -> assertThat(issue.getStatus()).isEqualTo(CouponIssueStatus.AVAILABLE)
            );
        }

        @DisplayName("이미 발급받은 쿠폰이면, CONFLICT 예외가 발생한다.")
        @Test
        void throwsConflict_whenAlreadyIssued() {
            Coupon coupon = createValidCoupon(1L);
            couponIssueService.issue(coupon, 1L);

            CoreException result = assertThrows(CoreException.class,
                () -> couponIssueService.issue(coupon, 1L));
            assertThat(result.getErrorType()).isEqualTo(ErrorType.CONFLICT);
        }

        @DisplayName("만료된 쿠폰이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenExpired() {
            Coupon coupon = createExpiredCoupon(2L);

            CoreException result = assertThrows(CoreException.class,
                () -> couponIssueService.issue(coupon, 1L));
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("삭제된 쿠폰이면, NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFound_whenDeleted() {
            Coupon coupon = createDeletedCoupon(3L);

            CoreException result = assertThrows(CoreException.class,
                () -> couponIssueService.issue(coupon, 1L));
            assertThat(result.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }
    }

    @DisplayName("쿠폰 발급 내역을 조회할 때, ")
    @Nested
    class GetByIdAndUserId {

        @DisplayName("본인의 쿠폰이면, 반환한다.")
        @Test
        void returnsIssue_whenOwner() {
            Coupon coupon = createValidCoupon(1L);
            CouponIssue issue = couponIssueService.issue(coupon, 1L);

            CouponIssue result = couponIssueService.getByIdAndUserId(issue.getId(), 1L);

            assertThat(result.getId()).isEqualTo(issue.getId());
        }

        @DisplayName("다른 유저의 쿠폰이면, NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFound_whenNotOwner() {
            Coupon coupon = createValidCoupon(1L);
            CouponIssue issue = couponIssueService.issue(coupon, 1L);

            CoreException result = assertThrows(CoreException.class,
                () -> couponIssueService.getByIdAndUserId(issue.getId(), 999L));
            assertThat(result.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }
    }

    @DisplayName("쿠폰을 사용할 때, ")
    @Nested
    class UseCoupon {

        @DisplayName("AVAILABLE 상태이면, 사용 처리된다.")
        @Test
        void usesCoupon_whenAvailable() {
            Coupon coupon = createValidCoupon(1L);
            CouponIssue issue = couponIssueService.issue(coupon, 1L);

            CouponIssue used = couponIssueService.useCoupon(issue.getId(), 1L);

            assertAll(
                () -> assertThat(used.getStatus()).isEqualTo(CouponIssueStatus.USED),
                () -> assertThat(used.getUsedAt()).isNotNull()
            );
        }
    }

    @DisplayName("쿠폰을 복원할 때, ")
    @Nested
    class RestoreCoupon {

        @DisplayName("USED 상태이면, 복원된다.")
        @Test
        void restoresCoupon_whenUsed() {
            Coupon coupon = createValidCoupon(1L);
            CouponIssue issue = couponIssueService.issue(coupon, 1L);
            couponIssueService.useCoupon(issue.getId(), 1L);

            couponIssueService.restoreCoupon(issue.getId());

            CouponIssue restored = couponIssueService.getByIdAndUserId(issue.getId(), 1L);
            assertAll(
                () -> assertThat(restored.getStatus()).isEqualTo(CouponIssueStatus.AVAILABLE),
                () -> assertThat(restored.getUsedAt()).isNull()
            );
        }
    }

    @DisplayName("내 쿠폰 목록을 조회할 때, ")
    @Nested
    class GetMyIssues {

        @DisplayName("발급된 쿠폰이 있으면, 목록을 반환한다.")
        @Test
        void returnsIssues_whenIssuesExist() {
            Coupon coupon1 = createValidCoupon(1L);
            Coupon coupon2 = createValidCoupon(2L);
            couponIssueService.issue(coupon1, 1L);
            couponIssueService.issue(coupon2, 1L);

            var result = couponIssueService.getMyIssues(1L);

            assertThat(result).hasSize(2);
        }

        @DisplayName("다른 유저의 쿠폰은 포함되지 않는다.")
        @Test
        void doesNotIncludeOtherUsersIssues() {
            Coupon coupon = createValidCoupon(1L);
            couponIssueService.issue(coupon, 1L);

            var result = couponIssueService.getMyIssues(999L);

            assertThat(result).isEmpty();
        }
    }
}
