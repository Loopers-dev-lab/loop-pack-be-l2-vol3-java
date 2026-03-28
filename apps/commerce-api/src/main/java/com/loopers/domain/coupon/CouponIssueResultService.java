package com.loopers.domain.coupon;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Component
public class CouponIssueResultService {

    private final CouponIssueResultRepository couponIssueResultRepository;

    @Transactional
    public CouponIssueResultModel createPending(String requestId, Long couponId, Long memberId) {
        return couponIssueResultRepository.save(
                new CouponIssueResultModel(requestId, couponId, memberId));
    }

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

    @Transactional(readOnly = true)
    public CouponIssueResultModel getByRequestId(String requestId) {
        return couponIssueResultRepository.findByRequestId(requestId)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "발급 요청을 찾을 수 없습니다."));
    }
}
