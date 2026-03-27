package com.loopers.domain.coupon;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

public class CouponIssueRequestTest {

    @DisplayName("발급 요청 생성 시,")
    @Nested
    class Create {

        @DisplayName("PENDING 상태로 생성된다.")
        @Test
        void createsWithPendingStatus() {
            // act
            CouponIssueRequest request = CouponIssueRequest.create(1L, 100L);

            // assert
            assertAll(
                () -> assertThat(request.getCouponId()).isEqualTo(1L),
                () -> assertThat(request.getUserId()).isEqualTo(100L),
                () -> assertThat(request.getStatus()).isEqualTo(CouponIssueRequest.Status.PENDING),
                () -> assertThat(request.getReason()).isNull()
            );
        }
    }

    @DisplayName("발급 성공 시,")
    @Nested
    class Succeed {

        @DisplayName("SUCCESS 상태로 변경된다.")
        @Test
        void changesStatusToSuccess() {
            // arrange
            CouponIssueRequest request = CouponIssueRequest.create(1L, 100L);

            // act
            request.succeed();

            // assert
            assertThat(request.getStatus()).isEqualTo(CouponIssueRequest.Status.SUCCESS);
        }
    }

    @DisplayName("발급 실패 시,")
    @Nested
    class Fail {

        @DisplayName("FAILED 상태와 실패 사유가 설정된다.")
        @Test
        void changesStatusToFailedWithReason() {
            // arrange
            CouponIssueRequest request = CouponIssueRequest.create(1L, 100L);
            String reason = "선착순 쿠폰이 모두 소진되었습니다.";

            // act
            request.fail(reason);

            // assert
            assertAll(
                () -> assertThat(request.getStatus()).isEqualTo(CouponIssueRequest.Status.FAILED),
                () -> assertThat(request.getReason()).isEqualTo(reason)
            );
        }
    }
}
