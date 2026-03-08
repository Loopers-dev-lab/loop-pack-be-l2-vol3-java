package com.loopers.domain.coupon;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CouponIssueTest {

    @DisplayName("CouponIssue를 생성할 때, ")
    @Nested
    class Create {

        @DisplayName("올바른 정보이면, AVAILABLE 상태로 생성된다.")
        @Test
        void createsIssue_whenValidInfo() {
            CouponIssue issue = new CouponIssue(1L, 1L);

            assertAll(
                () -> assertThat(issue.getCouponId()).isEqualTo(1L),
                () -> assertThat(issue.getUserId()).isEqualTo(1L),
                () -> assertThat(issue.getStatus()).isEqualTo(CouponIssueStatus.AVAILABLE),
                () -> assertThat(issue.getUsedAt()).isNull()
            );
        }

        @DisplayName("couponId가 null이면, 예외가 발생한다.")
        @Test
        void throwsException_whenCouponIdIsNull() {
            assertThrows(NullPointerException.class,
                () -> new CouponIssue(null, 1L));
        }

        @DisplayName("userId가 null이면, 예외가 발생한다.")
        @Test
        void throwsException_whenUserIdIsNull() {
            assertThrows(NullPointerException.class,
                () -> new CouponIssue(1L, null));
        }
    }

    @DisplayName("쿠폰을 사용할 때, ")
    @Nested
    class Use {

        @DisplayName("AVAILABLE 상태이면, USED로 전이된다.")
        @Test
        void transitionsToUsed_whenAvailable() {
            CouponIssue issue = new CouponIssue(1L, 1L);

            issue.use();

            assertAll(
                () -> assertThat(issue.getStatus()).isEqualTo(CouponIssueStatus.USED),
                () -> assertThat(issue.getUsedAt()).isNotNull()
            );
        }

        @DisplayName("이미 USED 상태이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenAlreadyUsed() {
            CouponIssue issue = new CouponIssue(1L, 1L);
            issue.use();

            CoreException result = assertThrows(CoreException.class, issue::use);
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @DisplayName("쿠폰을 복원할 때, ")
    @Nested
    class Restore {

        @DisplayName("USED 상태이면, AVAILABLE로 전이된다.")
        @Test
        void transitionsToAvailable_whenUsed() {
            CouponIssue issue = new CouponIssue(1L, 1L);
            issue.use();

            issue.restore();

            assertAll(
                () -> assertThat(issue.getStatus()).isEqualTo(CouponIssueStatus.AVAILABLE),
                () -> assertThat(issue.getUsedAt()).isNull()
            );
        }

        @DisplayName("AVAILABLE 상태이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenAlreadyAvailable() {
            CouponIssue issue = new CouponIssue(1L, 1L);

            CoreException result = assertThrows(CoreException.class, issue::restore);
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }
}
