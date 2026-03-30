package com.loopers.domain.coupon;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CouponIssueRequestTest {

    @DisplayName("CouponIssueRequest 를 생성할 때, ")
    @Nested
    class Create {

        @DisplayName("정상적인 값으로 생성하면, status 가 PENDING 인 요청이 생성된다.")
        @Test
        void createsCouponIssueRequest_withPendingStatus() {
            // arrange
            String requestId = "test-request-id";
            Long couponId = 1L;
            Long userId = 2L;

            // act
            CouponIssueRequest result = CouponIssueRequest.of(requestId, couponId, userId);

            // assert
            assertThat(result.requestId()).isEqualTo(requestId);
            assertThat(result.couponId()).isEqualTo(couponId);
            assertThat(result.userId()).isEqualTo(userId);
            assertThat(result.status()).isEqualTo(CouponIssueStatus.PENDING);
            assertThat(result.failReason()).isNull();
        }
    }

    @DisplayName("CouponIssueRequest 의 상태를 변경할 때, ")
    @Nested
    class StatusChange {

        @DisplayName("markSuccess() 를 호출하면, status 가 SUCCESS 로 변경된다.")
        @Test
        void changesStatusToSuccess_whenMarkSuccessCalled() {
            // arrange
            CouponIssueRequest request = CouponIssueRequest.of("req-id", 1L, 2L);

            // act
            request.markSuccess();

            // assert
            assertThat(request.status()).isEqualTo(CouponIssueStatus.SUCCESS);
        }

        @DisplayName("markFailed(reason) 를 호출하면, status 가 FAILED 로 변경되고 failReason 이 저장된다.")
        @Test
        void changesStatusToFailed_whenMarkFailedCalled() {
            // arrange
            CouponIssueRequest request = CouponIssueRequest.of("req-id", 1L, 2L);
            String reason = "수량 초과";

            // act
            request.markFailed(reason);

            // assert
            assertThat(request.status()).isEqualTo(CouponIssueStatus.FAILED);
            assertThat(request.failReason()).isEqualTo(reason);
        }
    }
}
