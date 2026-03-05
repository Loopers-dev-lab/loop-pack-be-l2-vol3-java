package com.loopers.domain.coupon;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.ZonedDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CouponIssueTest {

    private static final ZonedDateTime NOW = ZonedDateTime.now();

    @Nested
    @DisplayName("쿠폰 사용")
    class Use {

        @DisplayName("AVAILABLE 상태의 쿠폰을 사용하면 USED로 변경된다")
        @Test
        void use_whenAvailable_changesStatusToUsed() {
            CouponIssue issue = new CouponIssue(1L, 1L, NOW.plusDays(30));

            issue.use(100L, NOW);

            assertThat(issue.getStatus()).isEqualTo(CouponIssueStatus.USED);
            assertThat(issue.getUsedOrderId()).isEqualTo(100L);
        }

        @DisplayName("이미 사용된 쿠폰을 다시 사용하면 예외가 발생한다")
        @Test
        void use_whenAlreadyUsed_throwsException() {
            CouponIssue issue = new CouponIssue(1L, 1L, NOW.plusDays(30));
            issue.use(100L, NOW);

            assertThatThrownBy(() -> issue.use(200L, NOW))
                .isInstanceOf(CoreException.class)
                .extracting(e -> ((CoreException) e).getErrorType())
                .isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("만료된 쿠폰을 사용하면 예외가 발생한다")
        @Test
        void use_whenExpired_throwsException() {
            CouponIssue issue = new CouponIssue(1L, 1L, NOW.minusDays(1));

            assertThatThrownBy(() -> issue.use(100L, NOW))
                .isInstanceOf(CoreException.class)
                .extracting(e -> ((CoreException) e).getErrorType())
                .isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @Nested
    @DisplayName("쿠폰 사용 취소")
    class CancelUse {

        @DisplayName("USED 상태의 쿠폰을 복원하면 AVAILABLE로 변경된다")
        @Test
        void cancelUse_whenUsed_changesStatusToAvailable() {
            CouponIssue issue = new CouponIssue(1L, 1L, NOW.plusDays(30));
            issue.use(100L, NOW);

            issue.cancelUse();

            assertThat(issue.getStatus()).isEqualTo(CouponIssueStatus.AVAILABLE);
            assertThat(issue.getUsedOrderId()).isNull();
        }

        @DisplayName("AVAILABLE 상태에서 복원하면 예외가 발생한다")
        @Test
        void cancelUse_whenAvailable_throwsException() {
            CouponIssue issue = new CouponIssue(1L, 1L, NOW.plusDays(30));

            assertThatThrownBy(issue::cancelUse)
                .isInstanceOf(CoreException.class)
                .extracting(e -> ((CoreException) e).getErrorType())
                .isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @Nested
    @DisplayName("만료 여부 확인")
    class IsExpired {

        @DisplayName("만료 시간이 지났으면 true를 반환한다")
        @Test
        void isExpired_whenPastExpiredAt_returnsTrue() {
            CouponIssue issue = new CouponIssue(1L, 1L, NOW.minusDays(1));

            assertThat(issue.isExpired(NOW)).isTrue();
        }

        @DisplayName("만료 시간 이전이면 false를 반환한다")
        @Test
        void isExpired_whenBeforeExpiredAt_returnsFalse() {
            CouponIssue issue = new CouponIssue(1L, 1L, NOW.plusDays(30));

            assertThat(issue.isExpired(NOW)).isFalse();
        }
    }

    @Nested
    @DisplayName("유효 상태 조회")
    class GetEffectiveStatus {

        @DisplayName("AVAILABLE이지만 만료 시간이 지났으면 EXPIRED를 반환한다")
        @Test
        void getEffectiveStatus_whenAvailableButExpired_returnsExpired() {
            CouponIssue issue = new CouponIssue(1L, 1L, NOW.minusDays(1));

            assertThat(issue.getEffectiveStatus(NOW)).isEqualTo(CouponIssueStatus.EXPIRED);
        }

        @DisplayName("AVAILABLE이고 만료되지 않았으면 AVAILABLE을 반환한다")
        @Test
        void getEffectiveStatus_whenAvailableAndNotExpired_returnsAvailable() {
            CouponIssue issue = new CouponIssue(1L, 1L, NOW.plusDays(30));

            assertThat(issue.getEffectiveStatus(NOW)).isEqualTo(CouponIssueStatus.AVAILABLE);
        }

        @DisplayName("USED 상태이면 만료 여부와 관계없이 USED를 반환한다")
        @Test
        void getEffectiveStatus_whenUsed_returnsUsed() {
            CouponIssue issue = new CouponIssue(1L, 1L, NOW.plusDays(30));
            issue.use(100L, NOW);

            assertThat(issue.getEffectiveStatus(NOW)).isEqualTo(CouponIssueStatus.USED);
        }
    }
}
