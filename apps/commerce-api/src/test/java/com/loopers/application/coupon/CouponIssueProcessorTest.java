package com.loopers.application.coupon;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import static org.mockito.Mockito.*;

class CouponIssueProcessorTest {

    private CouponIssueTxService couponIssueTxService;
    private CouponIssueProcessor processor;

    @BeforeEach
    void setUp() {
        couponIssueTxService = mock(CouponIssueTxService.class);
        processor = new CouponIssueProcessor(couponIssueTxService);
    }

    @DisplayName("process: 정상 발급 시 tryIssue를 호출한다")
    @Test
    void process_success() {
        processor.process(1L);

        verify(couponIssueTxService).tryIssue(1L);
        verify(couponIssueTxService, never()).markFailed(anyLong(), anyString());
    }

    @DisplayName("process: DataIntegrityViolationException 발생 시 중복 발급으로 FAILED 처리한다")
    @Test
    void process_duplicateException_marksFailed() {
        doThrow(new DataIntegrityViolationException("duplicate"))
            .when(couponIssueTxService).tryIssue(1L);

        processor.process(1L);

        verify(couponIssueTxService).markFailed(1L, "중복 발급 요청");
    }

    @DisplayName("process: 예상치 못한 예외 발생 시 오류로 FAILED 처리한다")
    @Test
    void process_unexpectedException_marksFailed() {
        doThrow(new RuntimeException("unexpected"))
            .when(couponIssueTxService).tryIssue(1L);

        processor.process(1L);

        verify(couponIssueTxService).markFailed(1L, "발급 처리 오류");
    }
}
