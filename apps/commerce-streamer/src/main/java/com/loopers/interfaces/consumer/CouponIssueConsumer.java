package com.loopers.interfaces.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.confg.kafka.KafkaConfig;
import com.loopers.domain.event.model.EventHandleStatus;
import com.loopers.infrastructure.coupon.entity.CouponIssueRequestStreamerEntity;
import com.loopers.infrastructure.coupon.entity.UserCouponStreamerEntity;
import com.loopers.infrastructure.coupon.repository.CouponIssueRequestStreamerJpaRepository;
import com.loopers.infrastructure.coupon.repository.UserCouponStreamerJpaRepository;
import com.loopers.infrastructure.event.entity.EventHandledEntity;
import com.loopers.infrastructure.event.repository.EventHandledJpaRepository;
import com.loopers.interfaces.consumer.dto.CouponIssueMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;

@Slf4j
@RequiredArgsConstructor
@Component
public class CouponIssueConsumer {

    private final EventHandledJpaRepository eventHandledRepository;
    private final UserCouponStreamerJpaRepository userCouponRepository;
    private final CouponIssueRequestStreamerJpaRepository couponIssueRequestRepository;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;

    @KafkaListener(topics = "coupon-issue-requests", containerFactory = KafkaConfig.BATCH_LISTENER)
    public void consume(List<ConsumerRecord<Object, Object>> records, Acknowledgment ack) {
        for (ConsumerRecord<Object, Object> record : records) {
            try {
                transactionTemplate.executeWithoutResult(status -> processRecord(record));
            } catch (Exception e) {
                log.error("coupon-issue-requests 처리 실패 - record: {}", record, e);
            }
        }
        ack.acknowledge();
    }

    private void processRecord(ConsumerRecord<Object, Object> record) {
        CouponIssueMessage msg = objectMapper.convertValue(record.value(), CouponIssueMessage.class);
        String eventId = "coupon-issue-" + msg.requestId();

        if (eventHandledRepository.existsById(eventId)) {
            return;
        }

        UserCouponStreamerEntity userCoupon = UserCouponStreamerEntity.create(
                msg.couponTemplateId(), msg.memberId());
        userCouponRepository.save(userCoupon);

        couponIssueRequestRepository.findById(msg.requestId())
                .ifPresentOrElse(
                        CouponIssueRequestStreamerEntity::markIssued,
                        () -> log.warn("쿠폰 발급 요청을 찾을 수 없음 - requestId: {}", msg.requestId())
                );

        eventHandledRepository.save(EventHandledEntity.of(eventId, EventHandleStatus.SUCCESS));

        log.info("쿠폰 발급 완료 - requestId: {}, templateId: {}, memberId: {}",
                msg.requestId(), msg.couponTemplateId(), msg.memberId());
    }
}
