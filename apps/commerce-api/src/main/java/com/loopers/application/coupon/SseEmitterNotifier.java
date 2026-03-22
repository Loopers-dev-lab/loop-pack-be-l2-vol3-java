package com.loopers.application.coupon;

import com.loopers.domain.coupon.CouponIssueRequestModel;
import com.loopers.domain.coupon.CouponIssueRequestRepository;
import com.loopers.domain.coupon.CouponIssueStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class SseEmitterNotifier {

    private final SseEmitterRegistry sseEmitterRegistry;
    private final CouponIssueRequestRepository couponIssueRequestRepository;

    @Scheduled(fixedDelay = 500)
    public void notifyCompleted() {
        Set<String> pendingIds = sseEmitterRegistry.getPendingRequestIds();
        if (pendingIds.isEmpty()) {
            return;
        }
        List<CouponIssueRequestModel> completed = couponIssueRequestRepository
                .findByRequestIdInAndStatusIn(
                        new ArrayList<>(pendingIds),
                        List.of(CouponIssueStatus.ISSUED, CouponIssueStatus.REJECTED));
        for (CouponIssueRequestModel model : completed) {
            sseEmitterRegistry.complete(model.getRequestId(), model.getStatus());
        }
    }
}
