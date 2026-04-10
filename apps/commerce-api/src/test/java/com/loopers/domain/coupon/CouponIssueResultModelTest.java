package com.loopers.domain.coupon;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("CouponIssueResultModel 단위 테스트")
class CouponIssueResultModelTest {

    @Test
    @DisplayName("issued() 호출 시 ISSUED 상태와 null reason으로 생성된다")
    void issued_ShouldCreateWithIssuedStatusAndNullReason() {
        // given
        String requestId = "req-001";
        Long userId = 1L;
        Long couponId = 100L;

        // when
        CouponIssueResultModel result = CouponIssueResultModel.issued(requestId, userId, couponId);

        // then
        assertThat(result.getRequestId()).isEqualTo(requestId);
        assertThat(result.getUserId()).isEqualTo(userId);
        assertThat(result.getCouponId()).isEqualTo(couponId);
        assertThat(result.getStatus()).isEqualTo(CouponIssueStatus.ISSUED);
        assertThat(result.getReason()).isNull();
    }

    @Test
    @DisplayName("rejected() 호출 시 REJECTED 상태와 reason 문자열로 생성된다")
    void rejected_ShouldCreateWithRejectedStatusAndReason() {
        // given
        String requestId = "req-002";
        Long userId = 2L;
        Long couponId = 200L;
        String reason = "수량 소진";

        // when
        CouponIssueResultModel result = CouponIssueResultModel.rejected(requestId, userId, couponId, reason);

        // then
        assertThat(result.getRequestId()).isEqualTo(requestId);
        assertThat(result.getUserId()).isEqualTo(userId);
        assertThat(result.getCouponId()).isEqualTo(couponId);
        assertThat(result.getStatus()).isEqualTo(CouponIssueStatus.REJECTED);
        assertThat(result.getReason()).isEqualTo(reason);
    }
}
