package com.loopers.application.coupon;

import com.loopers.application.outbox.OutboxEventService;
import com.loopers.domain.coupon.*;
import com.loopers.domain.user.UserRepository;
import com.loopers.domain.user.User;
import com.loopers.support.error.CoreException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CouponIssueFacadeTest {

    private CouponRepository couponRepository;
    private CouponIssueRequestRepository couponIssueRequestRepository;
    private UserRepository userRepository;
    private OutboxEventService outboxEventService;
    private CouponIssueFacade couponIssueFacade;

    @BeforeEach
    void setUp() {
        couponRepository = mock(CouponRepository.class);
        couponIssueRequestRepository = mock(CouponIssueRequestRepository.class);
        userRepository = mock(UserRepository.class);
        outboxEventService = mock(OutboxEventService.class);
        couponIssueFacade = new CouponIssueFacade(
            couponRepository, couponIssueRequestRepository, userRepository, outboxEventService
        );
    }

    @DisplayName("requestIssue: PENDING 상태 요청을 저장하고 Outbox에 기록 후 requestId를 반환한다")
    @Test
    void requestIssue_success() {
        Coupon coupon = Coupon.create("쿠폰", "설명", CouponType.FIXED,
            BigDecimal.valueOf(5000), ZonedDateTime.now().plusDays(30), 100);
        User user = mock(User.class);

        when(userRepository.findById(100L)).thenReturn(Optional.of(user));
        when(couponRepository.findById(1L)).thenReturn(Optional.of(coupon));
        when(couponIssueRequestRepository.save(any(CouponIssueRequest.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        CouponDto.CouponIssueRequestInfo result = couponIssueFacade.requestIssue(100L, 1L);

        assertThat(result.couponId()).isEqualTo(1L);
        assertThat(result.userId()).isEqualTo(100L);
        assertThat(result.status()).isEqualTo(CouponIssueRequestStatus.PENDING);
        verify(outboxEventService).save(eq("COUPON"), eq(1L), eq("COUPON_ISSUE_REQUESTED"), any());
    }

    @DisplayName("requestIssue: 존재하지 않는 사용자면 예외가 발생한다")
    @Test
    void requestIssue_userNotFound() {
        when(userRepository.findById(100L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> couponIssueFacade.requestIssue(100L, 1L))
            .isInstanceOf(CoreException.class);
        verify(outboxEventService, never()).save(anyString(), anyLong(), anyString(), any());
    }

    @DisplayName("requestIssue: 존재하지 않는 쿠폰이면 예외가 발생한다")
    @Test
    void requestIssue_couponNotFound() {
        User user = mock(User.class);
        when(userRepository.findById(100L)).thenReturn(Optional.of(user));
        when(couponRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> couponIssueFacade.requestIssue(100L, 1L))
            .isInstanceOf(CoreException.class);
        verify(outboxEventService, never()).save(anyString(), anyLong(), anyString(), any());
    }

    @DisplayName("getIssueRequestStatus: requestId로 발급 요청 상태를 조회한다")
    @Test
    void getIssueRequestStatus_success() {
        CouponIssueRequest request = CouponIssueRequest.create(1L, 100L);
        when(couponIssueRequestRepository.findById(1L)).thenReturn(Optional.of(request));

        CouponDto.CouponIssueRequestInfo result = couponIssueFacade.getIssueRequestStatus(1L);

        assertThat(result.couponId()).isEqualTo(1L);
        assertThat(result.status()).isEqualTo(CouponIssueRequestStatus.PENDING);
    }
}
