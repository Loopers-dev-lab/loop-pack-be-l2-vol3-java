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

    private static final int BATCH_SIZE = 500;

    @Scheduled(fixedDelay = 500)
    public void notifyCompleted() {
        List<String> pendingIdList = new ArrayList<>(sseEmitterRegistry.getPendingRequestIds());
        if (pendingIdList.isEmpty()) {
            return;
        }
        for (int i = 0; i < pendingIdList.size(); i += BATCH_SIZE) {
            List<String> batch = pendingIdList.subList(i, Math.min(i + BATCH_SIZE, pendingIdList.size()));
            List<CouponIssueRequestModel> completed = couponIssueRequestRepository
                    .findByRequestIdInAndStatusIn(batch, List.of(CouponIssueStatus.ISSUED, CouponIssueStatus.REJECTED));
            for (CouponIssueRequestModel model : completed) {
                sseEmitterRegistry.complete(model.getRequestId(), model.getStatus());
            }
        }
    }
}
