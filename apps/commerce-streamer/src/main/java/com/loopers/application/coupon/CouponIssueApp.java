package com.loopers.application.coupon;

import com.loopers.domain.coupon.CouponIssueService;
import com.loopers.domain.eventhandled.EventHandledModel;
import com.loopers.domain.eventhandled.EventHandledRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class CouponIssueApp {

    private static final String TOPIC = "coupon-issue-requests";

    private final CouponIssueService couponIssueService;
    private final EventHandledRepository eventHandledRepository;

    @Transactional
    public void processIssue(String eventId, String requestId, Long couponTemplateId, Long memberId) {
        if (eventHandledRepository.existsByEventId(eventId)) {
            return;
        }
        couponIssueService.processIssue(requestId, couponTemplateId, memberId);
        eventHandledRepository.save(EventHandledModel.create(eventId, TOPIC));
    }
}
