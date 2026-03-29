package com.loopers.application.coupon;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class CouponIssueProcessor {

    private final CouponIssueTxService couponIssueTxService;

    public void process(Long requestId) {
        try {
            couponIssueTxService.tryIssue(requestId);
        } catch (DataIntegrityViolationException e) {
            log.info("쿠폰 중복 발급 감지 - requestId: {}", requestId);
            couponIssueTxService.markFailed(requestId, "중복 발급 요청");
        } catch (Exception e) {
            log.warn("쿠폰 발급 처리 실패 - requestId: {}", requestId, e);
            couponIssueTxService.markFailed(requestId, "발급 처리 오류");
        }
    }
}
