package com.loopers.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.support.converter.ByteArrayJsonMessageConverter;

import java.util.HashMap;
import java.util.Map;

/**
 * Metrics / Ranking / 기타 내부 Consumer 전용 Kafka 설정.
 *
 * <p>제공 팩토리:
 * <ul>
 *   <li>{@link #SINGLE_LISTENER} — 단건 리스너 (coupon-issue-requests 등 단건 처리 경로)</li>
 *   <li>{@link #RANKING_BATCH_LISTENER} — R9 랭킹 파이프라인 배치 리스너 (catalog/order events)</li>
 * </ul>
 *
 * <p>R7 catalog/order metrics 단건 리스너는 주석 처리로 비활성화되었으며,
 * SINGLE_LISTENER 빈은 여전히 쿠폰 발급 Consumer 가 사용한다. (week9.md §9)
 */
@Configuration
public class MetricsKafkaConfig {

    /** 단건 리스너 팩토리 빈 이름. */
    public static final String SINGLE_LISTENER = "SINGLE_LISTENER_METRICS";

    /** R9 랭킹 배치 리스너 팩토리 빈 이름. */
    public static final String RANKING_BATCH_LISTENER = "RANKING_BATCH_LISTENER";

    // R9 배치 리스너 튜닝값 — modules/kafka/KafkaConfig 의 BATCH_LISTENER_DEFAULT 와 동일 기준.
    private static final int MAX_POLL_RECORDS = 3000;
    private static final int FETCH_MIN_BYTES = 1024 * 1024;         // 1MB
    private static final int FETCH_MAX_WAIT_MS = 5 * 1000;          // 5s
    private static final int SESSION_TIMEOUT_MS = 60 * 1000;        // 60s
    private static final int HEARTBEAT_INTERVAL_MS = 20 * 1000;     // 20s
    private static final int MAX_POLL_INTERVAL_MS = 2 * 60 * 1000;  // 2m

    /**
     * 단건 리스너 팩토리 — coupon-issue-requests 등에 사용된다.
     *
     * Manual ACK + 단건 처리.
     */
    @Bean(name = SINGLE_LISTENER)
    public ConcurrentKafkaListenerContainerFactory<Object, Object> singleListenerContainerFactory(
            KafkaProperties kafkaProperties,
            ObjectMapper objectMapper
    ) {
        Map<String, Object> consumerConfig = new HashMap<>(kafkaProperties.buildConsumerProperties());
        ConcurrentKafkaListenerContainerFactory<Object, Object> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(new DefaultKafkaConsumerFactory<>(consumerConfig));
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL);
        factory.setRecordMessageConverter(new ByteArrayJsonMessageConverter(objectMapper));
        factory.setConcurrency(3);
        factory.setBatchListener(false);
        return factory;
    }

    /**
     * R9 랭킹 배치 리스너 팩토리 — catalog-events / order-events 배치 처리.
     *
     * - {@code setBatchListener(true)} + {@link BatchMessagingMessageConverter}
     * - Manual ACK (실패 시 재전달)
     * - concurrency = 3
     * - max.poll.records = 3000
     */
    @Bean(name = RANKING_BATCH_LISTENER)
    public ConcurrentKafkaListenerContainerFactory<Object, Object> rankingBatchListenerContainerFactory(
            KafkaProperties kafkaProperties,
            ObjectMapper objectMapper
    ) {
        Map<String, Object> consumerConfig = new HashMap<>(kafkaProperties.buildConsumerProperties());
        consumerConfig.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, MAX_POLL_RECORDS);
        consumerConfig.put(ConsumerConfig.FETCH_MIN_BYTES_CONFIG, FETCH_MIN_BYTES);
        consumerConfig.put(ConsumerConfig.FETCH_MAX_WAIT_MS_CONFIG, FETCH_MAX_WAIT_MS);
        consumerConfig.put(ConsumerConfig.SESSION_TIMEOUT_MS_CONFIG, SESSION_TIMEOUT_MS);
        consumerConfig.put(ConsumerConfig.HEARTBEAT_INTERVAL_MS_CONFIG, HEARTBEAT_INTERVAL_MS);
        consumerConfig.put(ConsumerConfig.MAX_POLL_INTERVAL_MS_CONFIG, MAX_POLL_INTERVAL_MS);
        // 기본 ByteArrayDeserializer 를 String 으로 교체 — Consumer 가 ConsumerRecord<String, String> 을 받음
        consumerConfig.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        consumerConfig.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);

        ConcurrentKafkaListenerContainerFactory<Object, Object> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(new DefaultKafkaConsumerFactory<>(consumerConfig));
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL);
        // BatchMessagingMessageConverter 는 ConsumerRecord 원본을 그대로 받는 리스너에는 불필요 —
        // 오히려 value 타입 cast 충돌을 유발할 수 있으므로 생략한다.
        factory.setConcurrency(3);
        factory.setBatchListener(true);
        return factory;
    }
}
