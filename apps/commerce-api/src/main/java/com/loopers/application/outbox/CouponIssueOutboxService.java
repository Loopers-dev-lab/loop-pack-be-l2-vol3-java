package com.loopers.application.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.contract.coupon.CouponIssueRequestedEvent;
import com.loopers.domain.outbox.OutboxEvent;
import com.loopers.domain.outbox.OutboxEventRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.ZonedDateTime;
import java.util.UUID;

@Service
public class CouponIssueOutboxService {

    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    @Value("${loopers.kafka.topic.coupon-issue-requested:commerce.coupon.issue-requested.v1}")
    private String couponIssueRequestedTopic;

    public CouponIssueOutboxService(OutboxEventRepository outboxEventRepository, ObjectMapper objectMapper) {
        this.outboxEventRepository = outboxEventRepository;
        this.objectMapper = objectMapper;
    }

    public void saveCouponIssueRequested(CouponIssueRequestedEvent event) {
        outboxEventRepository.save(OutboxEvent.pending(
                event.requestId(),
                "COUPON_ISSUE_REQUESTED",
                "coupon",
                event.couponId().toString(),
                couponIssueRequestedTopic,
                event.couponId().toString(),
                toJson(event),
                ZonedDateTime.now()
        ));
    }

    public String toJson(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new CoreException(ErrorType.INTERNAL_ERROR, "쿠폰 발급 outbox 직렬화에 실패했습니다.");
        }
    }
}
