package com.loopers.interfaces.consumer;

import com.loopers.domain.coupon.CouponIssueRequestMessage;
import com.loopers.domain.coupon.CouponIssueResultModel;
import com.loopers.domain.coupon.CouponIssueResultRepository;
import com.loopers.domain.coupon.CouponIssueStatus;
import com.loopers.domain.coupon.CouponRepository;
import com.loopers.domain.coupon.UserCouponRepository;
import com.loopers.domain.idempotency.EventHandledModel;
import com.loopers.domain.idempotency.EventHandledRepository;
import com.loopers.infrastructure.monitoring.ConsumerMetrics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("CouponIssueProcessor 단위 테스트")
class CouponIssueProcessorTest {

    @Mock
    CouponRepository couponRepository;

    @Mock
    UserCouponRepository userCouponRepository;

    @Mock
    CouponIssueResultRepository couponIssueResultRepository;

    @Mock
    EventHandledRepository eventHandledRepository;

    @Mock
    StringRedisTemplate stringRedisTemplate;

    @Mock
    ConsumerMetrics consumerMetrics;

    @Mock
    ValueOperations<String, String> valueOps;

    @InjectMocks
    CouponIssueProcessor couponIssueProcessor;

    private static final String REQUEST_ID = "550e8400-e29b-41d4-a716-446655440000";

    private CouponIssueRequestMessage createMessage(String requestId) {
        return new CouponIssueRequestMessage(requestId, 1L, 100L, LocalDateTime.now());
    }

    @Test
    @DisplayName("성공: eventHandled=false, existsByStatus=false, CAS=1 → userCoupon 저장 + result ISSUED 저장 + eventHandled 저장")
    void process_Success_ShouldSaveUserCouponAndResultAndEventHandled() {
        // given
        CouponIssueRequestMessage message = createMessage(REQUEST_ID);
        Long eventId = (long) REQUEST_ID.hashCode();

        when(eventHandledRepository.existsById(eventId)).thenReturn(false);
        when(couponIssueResultRepository.existsByUserIdAndCouponIdAndStatus(
                1L, 100L, CouponIssueStatus.ISSUED)).thenReturn(false);
        when(couponRepository.incrementIssuedCountWithCas(100L)).thenReturn(1);

        // when
        couponIssueProcessor.process(message);

        // then
        verify(userCouponRepository).save(any());
        verify(couponIssueResultRepository).save(argThat(result ->
                result.getStatus() == CouponIssueStatus.ISSUED));
        verify(eventHandledRepository).save(any(EventHandledModel.class));
    }

    @Test
    @DisplayName("Layer 2 스킵: eventHandled=true → CAS 호출하지 않고 save 하지 않는다")
    void process_EventHandledTrue_ShouldSkipProcessing() {
        // given
        CouponIssueRequestMessage message = createMessage(REQUEST_ID);
        Long eventId = (long) REQUEST_ID.hashCode();

        when(eventHandledRepository.existsById(eventId)).thenReturn(true);

        // when
        couponIssueProcessor.process(message);

        // then
        verify(couponRepository, never()).incrementIssuedCountWithCas(anyLong());
        verify(couponIssueResultRepository, never()).save(any());
        verify(userCouponRepository, never()).save(any());
    }

    @Test
    @DisplayName("Layer 3 중복 발급: eventHandled=false, existsByStatus=true → result REJECTED(중복 발급) 저장")
    void process_DuplicateIssue_ShouldSaveRejectedResult() {
        // given
        CouponIssueRequestMessage message = createMessage(REQUEST_ID);
        Long eventId = (long) REQUEST_ID.hashCode();

        when(eventHandledRepository.existsById(eventId)).thenReturn(false);
        when(couponIssueResultRepository.existsByUserIdAndCouponIdAndStatus(
                1L, 100L, CouponIssueStatus.ISSUED)).thenReturn(true);

        // when
        couponIssueProcessor.process(message);

        // then
        verify(couponIssueResultRepository).save(argThat(result ->
                result.getStatus() == CouponIssueStatus.REJECTED
                        && "중복 발급".equals(result.getReason())));
        verify(couponRepository, never()).incrementIssuedCountWithCas(anyLong());
    }

    @Test
    @DisplayName("Layer 4 수량 소진: eventHandled=false, existsByStatus=false, CAS=0 → result REJECTED(수량 소진) 저장 + Redis increment")
    void process_SoldOut_ShouldSaveRejectedAndIncrementRedis() {
        // given
        CouponIssueRequestMessage message = createMessage(REQUEST_ID);
        Long eventId = (long) REQUEST_ID.hashCode();

        when(eventHandledRepository.existsById(eventId)).thenReturn(false);
        when(couponIssueResultRepository.existsByUserIdAndCouponIdAndStatus(
                1L, 100L, CouponIssueStatus.ISSUED)).thenReturn(false);
        when(couponRepository.incrementIssuedCountWithCas(100L)).thenReturn(0);
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOps);

        // when
        couponIssueProcessor.process(message);

        // then
        verify(couponIssueResultRepository).save(argThat(result ->
                result.getStatus() == CouponIssueStatus.REJECTED
                        && "수량 소진".equals(result.getReason())));
        verify(valueOps).increment("coupon:remaining:100");
        verify(userCouponRepository, never()).save(any());
    }
}
