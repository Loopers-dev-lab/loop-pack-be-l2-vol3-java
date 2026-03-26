package com.loopers.application.coupon;

import com.loopers.domain.coupon.CouponIssueResultRepository;
import com.loopers.domain.coupon.CouponModel;
import com.loopers.domain.coupon.CouponService;
import com.loopers.infrastructure.monitoring.EventMetrics;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("CouponIssueFacade 단위 테스트")
class CouponIssueFacadeTest {

    @Mock
    KafkaTemplate<Object, Object> kafkaTemplate;

    @Mock
    StringRedisTemplate stringRedisTemplate;

    @Mock
    ValueOperations<String, String> valueOps;

    @Mock
    CouponIssueResultRepository couponIssueResultRepository;

    @Mock
    CouponService couponService;

    @Mock
    ObjectMapper objectMapper;

    @Mock
    EventMetrics eventMetrics;

    @InjectMocks
    CouponIssueFacade couponIssueFacade;

    private CouponModel rushCoupon;

    @BeforeEach
    void setUp() {
        rushCoupon = CouponModel.createRush(
                "선착순쿠폰",
                com.loopers.support.enums.DiscountType.FIXED,
                java.math.BigDecimal.valueOf(1000),
                null,
                java.time.LocalDateTime.now().plusDays(30),
                100
        );
    }

    @Test
    @DisplayName("성공: SETNX=true, DECR=99, kafka 성공 시 requestId를 반환한다")
    void requestRushIssue_Success_ShouldReturnRequestId() throws Exception {
        // given
        when(couponService.findByIdForAdmin(100L)).thenReturn(rushCoupon);
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.setIfAbsent(anyString(), eq("1"))).thenReturn(true);
        when(valueOps.decrement(anyString())).thenReturn(99L);
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");
        when(kafkaTemplate.send(anyString(), anyString(), anyString()))
                .thenReturn(CompletableFuture.completedFuture(null));

        // when
        String requestId = couponIssueFacade.requestRushIssue(1L, 100L);

        // then
        assertThat(requestId).isNotNull();
        verify(kafkaTemplate).send(eq("coupon-issue-requests"), anyString(), anyString());
    }

    @Test
    @DisplayName("중복 요청: SETNX=false 시 COUPON_ALREADY_ISSUED 예외가 발생한다")
    void requestRushIssue_Duplicate_ShouldThrowCouponAlreadyIssued() {
        // given
        when(couponService.findByIdForAdmin(100L)).thenReturn(rushCoupon);
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.setIfAbsent(anyString(), eq("1"))).thenReturn(false);

        // when & then
        assertThatThrownBy(() -> couponIssueFacade.requestRushIssue(1L, 100L))
                .isInstanceOf(CoreException.class)
                .satisfies(e -> assertThat(((CoreException) e).getErrorType())
                        .isEqualTo(ErrorType.COUPON_ALREADY_ISSUED));
    }

    @Test
    @DisplayName("수량 소진: SETNX=true, DECR=-1 시 예외 발생 및 increment 복원된다")
    void requestRushIssue_SoldOut_ShouldThrowAndRestoreIncrement() {
        // given
        when(couponService.findByIdForAdmin(100L)).thenReturn(rushCoupon);
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.setIfAbsent(anyString(), eq("1"))).thenReturn(true);
        when(valueOps.decrement(anyString())).thenReturn(-1L);

        // when & then
        assertThatThrownBy(() -> couponIssueFacade.requestRushIssue(1L, 100L))
                .isInstanceOf(CoreException.class)
                .satisfies(e -> assertThat(((CoreException) e).getErrorType())
                        .isEqualTo(ErrorType.COUPON_NOT_AVAILABLE));

        verify(valueOps).increment(anyString());
        verify(stringRedisTemplate).delete(anyString());
    }

    @Test
    @DisplayName("Kafka 실패: SETNX=true, DECR=50, kafka 예외 시 increment + delete 복원된다")
    void requestRushIssue_KafkaFail_ShouldRestoreIncrementAndDelete() throws Exception {
        // given
        when(couponService.findByIdForAdmin(100L)).thenReturn(rushCoupon);
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.setIfAbsent(anyString(), eq("1"))).thenReturn(true);
        when(valueOps.decrement(anyString())).thenReturn(50L);
        when(objectMapper.writeValueAsString(any())).thenThrow(new RuntimeException("Kafka send failed"));

        // when & then
        assertThatThrownBy(() -> couponIssueFacade.requestRushIssue(1L, 100L))
                .isInstanceOf(CoreException.class)
                .satisfies(e -> assertThat(((CoreException) e).getErrorType())
                        .isEqualTo(ErrorType.INTERNAL_ERROR));

        verify(valueOps).increment(anyString());
        verify(stringRedisTemplate).delete(anyString());
    }
}
