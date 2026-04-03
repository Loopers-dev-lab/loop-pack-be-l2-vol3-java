package com.loopers.domain.coupon;

import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class CouponIssueRequestTest {

    @Nested
    class 생성 {

        @Test
        void 유효한_값이면_PENDING_상태로_생성된다() {
            CouponIssueRequest request = CouponIssueRequest.create("event-1", 1L, 100L);

            assertAll(
                    () -> assertThat(request.getEventId()).isEqualTo("event-1"),
                    () -> assertThat(request.getCouponId()).isEqualTo(1L),
                    () -> assertThat(request.getUserId()).isEqualTo(100L),
                    () -> assertThat(request.getStatus()).isEqualTo(CouponIssueStatus.PENDING),
                    () -> assertThat(request.getCreatedAt()).isNotNull()
            );
        }
    }

    @Nested
    class 상태_확인 {

        @Test
        void PENDING_상태이면_isPending이_true이다() {
            CouponIssueRequest request = CouponIssueRequest.create("event-1", 1L, 100L);

            assertThat(request.isPending()).isTrue();
        }
    }
}
