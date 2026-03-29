package com.loopers.application.coupon;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@DisplayName("CouponIssueRequestAppService 단위 테스트")
class CouponIssueRequestAppServiceTest {

    private CouponIssueRequestAppService couponIssueRequestAppService;
    private CouponIssueMessagePublisher messagePublisher;
    private StringRedisTemplate redisTemplate;
    private ValueOperations<String, String> valueOperations;

    @BeforeEach
    void setUp() {
        messagePublisher = mock(CouponIssueMessagePublisher.class);
        redisTemplate = mock(StringRedisTemplate.class);
        valueOperations = mock(ValueOperations.class);
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        couponIssueRequestAppService = new CouponIssueRequestAppService(messagePublisher, redisTemplate);
    }

    @Nested
    @DisplayName("쿠폰 비동기 발급 요청")
    class RequestCouponIssueTest {

        @Test
        @DisplayName("요청 시 requestId를 반환하고 Kafka에 메시지를 발행한다")
        void requestCouponIssue_publishesMessage() {
            // when
            String requestId = couponIssueRequestAppService.requestCouponIssue(1L, 100L);

            // then
            assertThat(requestId).isNotBlank();
            verify(messagePublisher).publish(any(CouponIssueMessage.class));
        }

        @Test
        @DisplayName("요청 시 Redis에 PENDING 상태를 저장한다")
        void requestCouponIssue_storesPendingStatus() {
            // when
            String requestId = couponIssueRequestAppService.requestCouponIssue(1L, 100L);

            // then
            verify(valueOperations).set(
                    eq("coupon:issue:status:" + requestId),
                    eq("PENDING"),
                    any(Duration.class)
            );
        }

        @Test
        @DisplayName("발행되는 메시지에 couponId와 userId가 정확히 담긴다")
        void requestCouponIssue_messageContainsCorrectData() {
            // when
            String requestId = couponIssueRequestAppService.requestCouponIssue(5L, 200L);

            // then
            verify(messagePublisher).publish(any(CouponIssueMessage.class));
            verify(messagePublisher).publish(org.mockito.ArgumentMatchers.argThat(msg ->
                    msg.couponId().equals(5L) &&
                    msg.userId().equals(200L) &&
                    msg.requestId().equals(requestId)
            ));
        }
    }

    @Nested
    @DisplayName("발급 요청 상태 조회")
    class GetIssueRequestStatusTest {

        @Test
        @DisplayName("존재하는 requestId의 상태를 반환한다")
        void getIssueRequestStatus_returnsStatus() {
            // given
            given(valueOperations.get("coupon:issue:status:req-123")).willReturn("SUCCESS");

            // when
            Optional<String> status = couponIssueRequestAppService.getIssueRequestStatus("req-123");

            // then
            assertThat(status).isPresent().contains("SUCCESS");
        }

        @Test
        @DisplayName("존재하지 않는 requestId는 빈 Optional을 반환한다")
        void getIssueRequestStatus_notFound_returnsEmpty() {
            // given
            given(valueOperations.get("coupon:issue:status:unknown")).willReturn(null);

            // when
            Optional<String> status = couponIssueRequestAppService.getIssueRequestStatus("unknown");

            // then
            assertThat(status).isEmpty();
        }
    }
}
