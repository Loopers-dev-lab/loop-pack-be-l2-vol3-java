package com.loopers.domain.coupon;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Component
public class CouponIssueResultService {

    private final CouponIssueResultRepository couponIssueResultRepository;

    @Transactional
    public void markSuccess(String requestId) {
        CouponIssueResultModel result = getByRequestId(requestId);
        result.markSuccess();
    }

    @Transactional
    public void markFailed(String requestId, String reason) {
        CouponIssueResultModel result = getByRequestId(requestId);
        result.markFailed(reason);
    }

    private CouponIssueResultModel getByRequestId(String requestId) {
        return couponIssueResultRepository.findByRequestId(requestId)
                .orElseThrow(() -> new EntityNotFoundException("발급 요청을 찾을 수 없습니다. requestId=" + requestId));
    }
}
