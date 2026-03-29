package com.loopers.domain.coupon;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CouponIssueRequestTest {

    @DisplayName("create: PENDING 상태의 발급 요청을 생성한다")
    @Test
    void create() {
        CouponIssueRequest request = CouponIssueRequest.create(1L, 100L);

        assertThat(request.getCouponId()).isEqualTo(1L);
        assertThat(request.getUserId()).isEqualTo(100L);
        assertThat(request.getStatus()).isEqualTo(CouponIssueRequestStatus.PENDING);
        assertThat(request.getFailReason()).isNull();
    }

    @DisplayName("success: 발급 성공 시 상태를 SUCCESS로 변경한다")
    @Test
    void success() {
        CouponIssueRequest request = CouponIssueRequest.create(1L, 100L);

        request.success();

        assertThat(request.getStatus()).isEqualTo(CouponIssueRequestStatus.SUCCESS);
    }

    @DisplayName("fail: 발급 실패 시 상태를 FAILED로 변경하고 사유를 기록한다")
    @Test
    void fail() {
        CouponIssueRequest request = CouponIssueRequest.create(1L, 100L);

        request.fail("수량 초과");

        assertThat(request.getStatus()).isEqualTo(CouponIssueRequestStatus.FAILED);
        assertThat(request.getFailReason()).isEqualTo("수량 초과");
    }
}
