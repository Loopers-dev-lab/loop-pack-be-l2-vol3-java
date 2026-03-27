package com.loopers.infrastructure.kafka;

import com.loopers.confg.kafka.KafkaConfig;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.converter.BatchMessagingMessageConverter;
import org.springframework.kafka.support.converter.ByteArrayJsonMessageConverter;
import org.springframework.util.backoff.FixedBackOff;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class StreamerKafkaConfig {

    public static final String DLQ_BATCH_LISTENER = "DLQ_BATCH_LISTENER";
    public static final String DLT_LISTENER = "DLT_LISTENER";

    // MAX_POLL_RECORDS 산출 근거:
    //   catalog consumer p99=51ms → 120000 × 0.7 / 51 ≈ 1647
    //   coupon  consumer p99=70ms → 120000 × 0.7 / 70 = 1200
    //   두 컨슈머 중 처리 비용이 높은 coupon 기준으로 보수적 적용
    private static final int MAX_POLL_RECORDS = 1200;

    // concurrency=3 근거:
    //   catalog-events, coupon-issue-requests 각 토픽의 파티션 수 = 3
    //   Consumer 스레드 수 = 파티션 수로 맞춰야 Scale-Out 효과 선형
    //   파티션 수를 변경하면 이 값도 함께 조정 필요
    private static final int CONCURRENCY = 3;

    @Bean(name = DLQ_BATCH_LISTENER)
    public ConcurrentKafkaListenerContainerFactory<Object, Object> dlqBatchListenerContainerFactory(
            KafkaProperties kafkaProperties,
            KafkaTemplate<Object, Object> kafkaTemplate,
            ByteArrayJsonMessageConverter converter
    ) {
        Map<String, Object> consumerConfig = new HashMap<>(kafkaProperties.buildConsumerProperties());
        consumerConfig.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, MAX_POLL_RECORDS);
        consumerConfig.put(ConsumerConfig.FETCH_MIN_BYTES_CONFIG, KafkaConfig.FETCH_MIN_BYTES);
        consumerConfig.put(ConsumerConfig.FETCH_MAX_WAIT_MS_CONFIG, KafkaConfig.FETCH_MAX_WAIT_MS);
        consumerConfig.put(ConsumerConfig.SESSION_TIMEOUT_MS_CONFIG, KafkaConfig.SESSION_TIMEOUT_MS);
        consumerConfig.put(ConsumerConfig.HEARTBEAT_INTERVAL_MS_CONFIG, KafkaConfig.HEARTBEAT_INTERVAL_MS);
        consumerConfig.put(ConsumerConfig.MAX_POLL_INTERVAL_MS_CONFIG, KafkaConfig.MAX_POLL_INTERVAL_MS);

        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(kafkaTemplate);
        DefaultErrorHandler errorHandler = new DefaultErrorHandler(recoverer, new FixedBackOff(1000L, 3L));
        errorHandler.addNotRetryableExceptions(IllegalStateException.class);

        ConcurrentKafkaListenerContainerFactory<Object, Object> factory = new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(new DefaultKafkaConsumerFactory<>(consumerConfig));
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL);
        factory.setBatchMessageConverter(new BatchMessagingMessageConverter(converter));
        factory.setConcurrency(CONCURRENCY);
        factory.setBatchListener(true);
        factory.setCommonErrorHandler(errorHandler);
        return factory;
    }

    // DLT Consumer 전용 팩토리: 재시도 없이 1회만 처리 후 commit.
    // DLT-of-DLT 연쇄를 방지하기 위해 DeadLetterPublishingRecoverer를 설정하지 않는다.
    @Bean(name = DLT_LISTENER)
    public ConcurrentKafkaListenerContainerFactory<Object, Object> dltListenerContainerFactory(
            KafkaProperties kafkaProperties
    ) {
        ConcurrentKafkaListenerContainerFactory<Object, Object> factory = new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(new DefaultKafkaConsumerFactory<>(
                new HashMap<>(kafkaProperties.buildConsumerProperties())));
        factory.setCommonErrorHandler(new DefaultErrorHandler(new FixedBackOff(0L, 0L)));
        return factory;
    }
}
