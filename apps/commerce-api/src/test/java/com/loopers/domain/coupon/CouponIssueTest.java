package com.loopers.domain.coupon;

import com.loopers.domain.coupon.exception.CouponNotAvailableException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.ZonedDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CouponIssueTest {

    private CouponIssue createAvailableIssue() {
        return CouponIssue.create(1L, 1L, ZonedDateTime.now().plusDays(7));
    }

    @DisplayName("isAvailable: AVAILABLE 상태이고 만료되지 않으면 사용 가능하다")
    @Test
    void isAvailable_true() {
        CouponIssue issue = createAvailableIssue();

        assertThat(issue.isAvailable()).isTrue();
    }

    @DisplayName("isAvailable: USED 상태이면 사용 불가능하다")
    @Test
    void isAvailable_false_whenUsed() {
        CouponIssue issue = createAvailableIssue();
        issue.use();

        assertThat(issue.isAvailable()).isFalse();
    }

    @DisplayName("isAvailable: 만료된 쿠폰은 사용 불가능하다")
    @Test
    void isAvailable_false_whenExpired() {
        CouponIssue issue = CouponIssue.create(1L, 1L, ZonedDateTime.now().minusDays(1));

        assertThat(issue.isAvailable()).isFalse();
    }

    @DisplayName("use: 사용 가능한 쿠폰을 사용 처리할 수 있다")
    @Test
    void use_success() {
        CouponIssue issue = createAvailableIssue();

        issue.use();

        assertThat(issue.getStatus()).isEqualTo(CouponIssueStatus.USED);
        assertThat(issue.getUsedAt()).isNotNull();
    }

    @DisplayName("use: 이미 사용된 쿠폰을 다시 사용하면 예외가 발생한다")
    @Test
    void use_fail_whenAlreadyUsed() {
        CouponIssue issue = createAvailableIssue();
        issue.use();

        assertThatThrownBy(issue::use)
            .isInstanceOf(CouponNotAvailableException.class);
    }

    @DisplayName("use: 만료된 쿠폰을 사용하면 예외가 발생한다")
    @Test
    void use_fail_whenExpired() {
        CouponIssue issue = CouponIssue.create(1L, 1L, ZonedDateTime.now().minusDays(1));

        assertThatThrownBy(issue::use)
            .isInstanceOf(CouponNotAvailableException.class);
    }

    @DisplayName("revoke: 사용된 쿠폰을 복구하면 AVAILABLE 상태로 돌아온다")
    @Test
    void revoke_success() {
        CouponIssue issue = createAvailableIssue();
        issue.use();

        issue.revoke();

        assertThat(issue.getStatus()).isEqualTo(CouponIssueStatus.AVAILABLE);
        assertThat(issue.getUsedAt()).isNull();
    }

    @DisplayName("create: couponId가 없으면 생성에 실패한다")
    @Test
    void create_fail_nullCouponId() {
        assertThatThrownBy(() -> CouponIssue.create(null, 1L, ZonedDateTime.now().plusDays(7)))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @DisplayName("create: userId가 없으면 생성에 실패한다")
    @Test
    void create_fail_nullUserId() {
        assertThatThrownBy(() -> CouponIssue.create(1L, null, ZonedDateTime.now().plusDays(7)))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @DisplayName("create: 만료일이 null이면 생성에 실패한다")
    @Test
    void create_fail_nullExpiredAt() {
        assertThatThrownBy(() -> CouponIssue.create(1L, 1L, null))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
