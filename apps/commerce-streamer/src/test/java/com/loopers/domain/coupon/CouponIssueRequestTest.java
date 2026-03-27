package com.loopers.domain.coupon;

import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class CouponIssueRequestTest {

    @Nested
    class 완료 {

        @Test
        void complete하면_상태가_COMPLETED가_된다() {
            CouponIssueRequest request = createPendingRequest();

            request.complete();

            assertThat(request.getStatus()).isEqualTo(CouponIssueStatus.COMPLETED);
        }

        @Test
        void complete하면_processedAt이_설정된다() {
            CouponIssueRequest request = createPendingRequest();

            request.complete();

            assertThat(request.getProcessedAt()).isNotNull();
        }
    }

    @Nested
    class 거절 {

        @Test
        void reject하면_상태가_REJECTED가_된다() {
            CouponIssueRequest request = createPendingRequest();

            request.reject("수량 소진");

            assertThat(request.getStatus()).isEqualTo(CouponIssueStatus.REJECTED);
        }

        @Test
        void reject하면_rejectReason이_기록된다() {
            CouponIssueRequest request = createPendingRequest();

            request.reject("수량 소진");

            assertThat(request.getRejectReason()).isEqualTo("수량 소진");
        }

        @Test
        void reject하면_processedAt이_설정된다() {
            CouponIssueRequest request = createPendingRequest();

            request.reject("수량 소진");

            assertThat(request.getProcessedAt()).isNotNull();
        }
    }

    @Nested
    class 상태_확인 {

        @Test
        void PENDING이면_isPending이_true이다() {
            CouponIssueRequest request = createPendingRequest();

            assertThat(request.isPending()).isTrue();
        }

        @Test
        void COMPLETED이면_isPending이_false이다() {
            CouponIssueRequest request = createPendingRequest();
            request.complete();

            assertThat(request.isPending()).isFalse();
        }
    }

    private CouponIssueRequest createPendingRequest() {
        // streamer의 CouponIssueRequest는 create() 팩토리가 없으므로 리플렉션으로 생성
        CouponIssueRequest request = new CouponIssueRequest();
        org.springframework.test.util.ReflectionTestUtils.setField(request, "status", CouponIssueStatus.PENDING);
        return request;
    }
}
