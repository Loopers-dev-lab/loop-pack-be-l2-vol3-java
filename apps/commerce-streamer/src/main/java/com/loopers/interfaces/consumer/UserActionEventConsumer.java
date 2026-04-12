package com.loopers.interfaces.consumer;

import com.loopers.confg.kafka.KafkaConfig;
import com.loopers.infrastructure.ranking.RankingRedisRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

@Slf4j
@RequiredArgsConstructor
@Component
public class UserActionEventConsumer {

    private static final String TOPIC = "user-action-events";
    private static final double VIEW_WEIGHT = 0.1;

    private final RankingRedisRepository rankingRedisRepository;

    /**
     * user-action-events 토픽의 유저 행동 이벤트를 소비하여
     * PRODUCT_VIEWED 이벤트의 랭킹 점수를 갱신한다.
     * - 멱등성: 조회 이벤트는 중복 처리 시 점수가 소폭 중복 적재될 수 있으나,
     * weight=0.1이므로 허용
     */
    @KafkaListener(topics = TOPIC, containerFactory = KafkaConfig.BATCH_LISTENER)
    public void handleUserActionEvents(
        List<ConsumerRecord<String, UserActionEventMessage>> messages,
        Acknowledgment acknowledgment
    ) {
        for (ConsumerRecord<String, UserActionEventMessage> record : messages) {
            UserActionEventMessage message = record.value();
            if (message == null) {
                continue;
            }
            try {
                if (!"PRODUCT_VIEWED".equals(message.eventType())) {
                    continue;
                }
                LocalDate eventDate = Instant.ofEpochMilli(record.timestamp())
                    .atZone(ZoneId.of("Asia/Seoul"))
                    .toLocalDate();
                rankingRedisRepository.incrementScore(eventDate, message.targetId(), VIEW_WEIGHT);
            } catch (Exception e) {
                log.error("UserActionEvent 랭킹 ZSET 갱신 실패 (무시): eventType={}, targetId={}",
                    message.eventType(), message.targetId(), e);
            }
        }
        acknowledgment.acknowledge();
    }

    public record UserActionEventMessage(String eventType, Long userId, Long targetId, String metadata) {
    }
}
