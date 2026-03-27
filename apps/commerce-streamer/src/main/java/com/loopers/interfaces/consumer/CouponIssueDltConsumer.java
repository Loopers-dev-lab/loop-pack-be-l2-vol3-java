package com.loopers.interfaces.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.domain.coupon.CouponIssueRequestRepository;
import com.loopers.domain.coupon.CouponIssueStatus;
import com.loopers.infrastructure.kafka.StreamerKafkaConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class CouponIssueDltConsumer {

    private static final String DLT_TOPIC = "coupon-issue-requests-dlt";

    private final CouponIssueRequestRepository couponIssueRequestRepository;
    private final ObjectMapper objectMapper;

    // DLT로 빠진 메시지 = Consumer가 3회 재시도 후에도 처리 실패한 쿠폰 발급 요청.
    // 이 시점에 API 측 coupon_issue_requests 레코드는 여전히 PENDING 상태다.
    // (처리 성공 시 event_handled도 함께 커밋되므로 DLT에 도달했다면 성공한 적 없음)
    //
    // PENDING 레코드를 삭제해 사용자가 재요청할 수 있도록 한다.
    // DLT 토픽 자체가 감사 로그 역할을 한다.
    //
    // ISSUED/REJECTED 상태인 경우(이론상 불가)에는 삭제하지 않고 경고만 남긴다.
    @KafkaListener(
            topics = DLT_TOPIC,
            groupId = "commerce-streamer-coupon-dlt",
            containerFactory = StreamerKafkaConfig.DLT_LISTENER
    )
    public void consume(ConsumerRecord<Object, Object> record) {
        try {
            CouponIssuePayload payload = objectMapper.readValue((byte[]) record.value(), CouponIssuePayload.class);
            couponIssueRequestRepository.findByRequestId(payload.requestId())
                    .ifPresentOrElse(
                            request -> {
                                if (request.getStatus() == CouponIssueStatus.PENDING) {
                                    couponIssueRequestRepository.delete(request);
                                    log.warn("[COUPON_DLT] requestId={} PENDING 삭제 완료 — 사용자 재요청 가능",
                                            payload.requestId());
                                } else {
                                    log.warn("[COUPON_DLT] requestId={} 상태={}이므로 삭제 생략",
                                            payload.requestId(), request.getStatus());
                                }
                            },
                            () -> log.warn("[COUPON_DLT] requestId={} 레코드 없음 — 이미 처리되었거나 수동 삭제됨",
                                    payload.requestId())
                    );
        } catch (Exception e) {
            log.error("[COUPON_DLT] payload 파싱 실패 — 수동 확인 필요. topic={}, partition={}, offset={}",
                    record.topic(), record.partition(), record.offset(), e);
        }
    }
}
