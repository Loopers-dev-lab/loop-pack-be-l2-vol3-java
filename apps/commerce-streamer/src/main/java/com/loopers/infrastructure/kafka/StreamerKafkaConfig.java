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

    // 벤치마크 결과: p99=51ms → MAX_POLL_INTERVAL_MS(120s) × 0.7 / 51ms ≈ 1647
    // KafkaConfig.MAX_POLLING_SIZE(3000) 사용 시 3000 × 51ms = 153s → MAX_POLL_INTERVAL 초과 위험
    private static final int MAX_POLL_RECORDS = 1600;

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

        ConcurrentKafkaListenerContainerFactory<Object, Object> factory = new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(new DefaultKafkaConsumerFactory<>(consumerConfig));
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL);
        factory.setBatchMessageConverter(new BatchMessagingMessageConverter(converter));
        factory.setConcurrency(3);
        factory.setBatchListener(true);
        factory.setCommonErrorHandler(errorHandler);
        return factory;
    }
}
