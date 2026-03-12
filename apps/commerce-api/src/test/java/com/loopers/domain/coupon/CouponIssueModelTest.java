package com.loopers.domain.coupon;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CouponIssueModelTest {

    private CouponIssueModel createIssue(LocalDateTime expiredAt) {
        return new CouponIssueModel(1L, 100L, expiredAt);
    }

    @DisplayName("쿠폰 발급 생성")
    @Nested
    class Create {

        @DisplayName("유효한 정보로 생성할 수 있다")
        @Test
        void createsWithValidInput() {
            // given & when
            CouponIssueModel issue = createIssue(LocalDateTime.of(2099, 12, 31, 23, 59));

            // then
            assertAll(
                () -> assertThat(issue.couponId()).isEqualTo(1L),
                () -> assertThat(issue.userId()).isEqualTo(100L),
                () -> assertThat(issue.status()).isEqualTo(CouponIssueStatus.AVAILABLE),
                () -> assertThat(issue.isUsed()).isFalse()
            );
        }

        @DisplayName("couponId가 null이면 예외가 발생한다")
        @Test
        void throwsWhenCouponIdNull() {
            CoreException result = assertThrows(CoreException.class,
                () -> new CouponIssueModel(null, 100L, LocalDateTime.of(2099, 12, 31, 23, 59)));
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @DisplayName("쿠폰 사용")
    @Nested
    class Use {

        @DisplayName("사용 가능한 쿠폰을 사용할 수 있다")
        @Test
        void usesAvailableCoupon() {
            // given
            CouponIssueModel issue = createIssue(LocalDateTime.of(2099, 12, 31, 23, 59));

            // when
            issue.use(999L);

            // then
            assertAll(
                () -> assertThat(issue.isUsed()).isTrue(),
                () -> assertThat(issue.status()).isEqualTo(CouponIssueStatus.USED),
                () -> assertThat(issue.orderId()).isEqualTo(999L)
            );
        }

        @DisplayName("이미 사용된 쿠폰은 다시 사용할 수 없다")
        @Test
        void throwsWhenAlreadyUsed() {
            // given
            CouponIssueModel issue = createIssue(LocalDateTime.of(2099, 12, 31, 23, 59));
            issue.use(999L);

            // when & then
            CoreException result = assertThrows(CoreException.class, () -> issue.use(1000L));
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("만료된 쿠폰은 사용할 수 없다")
        @Test
        void throwsWhenExpired() {
            // given
            CouponIssueModel issue = createIssue(LocalDateTime.of(2020, 1, 1, 0, 0));

            // when & then
            CoreException result = assertThrows(CoreException.class, () -> issue.use(999L));
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @DisplayName("소유자 검증")
    @Nested
    class ValidateOwner {

        @DisplayName("본인의 쿠폰이면 통과한다")
        @Test
        void passesForOwner() {
            CouponIssueModel issue = createIssue(LocalDateTime.of(2099, 12, 31, 23, 59));
            issue.validateOwner(100L); // 예외 없이 통과
        }

        @DisplayName("다른 사용자의 쿠폰이면 예외가 발생한다")
        @Test
        void throwsForNonOwner() {
            CouponIssueModel issue = createIssue(LocalDateTime.of(2099, 12, 31, 23, 59));
            CoreException result = assertThrows(CoreException.class,
                () -> issue.validateOwner(999L));
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @DisplayName("상태 판별")
    @Nested
    class Status {

        @DisplayName("사용 전 + 만료 전이면 AVAILABLE이다")
        @Test
        void availableWhenNotUsedNotExpired() {
            CouponIssueModel issue = createIssue(LocalDateTime.of(2099, 12, 31, 23, 59));
            assertThat(issue.status()).isEqualTo(CouponIssueStatus.AVAILABLE);
        }

        @DisplayName("사용 후에는 USED이다")
        @Test
        void usedAfterUse() {
            CouponIssueModel issue = createIssue(LocalDateTime.of(2099, 12, 31, 23, 59));
            issue.use(999L);
            assertThat(issue.status()).isEqualTo(CouponIssueStatus.USED);
        }

        @DisplayName("만료 후에는 EXPIRED이다")
        @Test
        void expiredWhenPastDate() {
            CouponIssueModel issue = createIssue(LocalDateTime.of(2020, 1, 1, 0, 0));
            assertThat(issue.status()).isEqualTo(CouponIssueStatus.EXPIRED);
        }
    }
}
