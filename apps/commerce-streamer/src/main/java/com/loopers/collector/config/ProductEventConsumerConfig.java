package com.loopers.collector.config;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.TopicPartition;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DefaultErrorHandler;
import com.loopers.collector.metrics.KafkaCollectorDlqMetrics;
import org.springframework.kafka.listener.ConsumerRecordRecoverer;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.util.backoff.FixedBackOff;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class ProductEventConsumerConfig {

    public static final String PRODUCT_EVENT_LISTENER = "PRODUCT_EVENT_LISTENER";

    @Bean(name = PRODUCT_EVENT_LISTENER)
    public ConcurrentKafkaListenerContainerFactory<Object, Object> productEventListenerContainerFactory(
            KafkaProperties kafkaProperties,
            KafkaTemplate<Object, Object> kafkaTemplate,
            KafkaCollectorDlqMetrics dlqMetrics,
            @Value("${collector.product.dlq-suffix:.DLQ}") String dlqSuffix
    ) {
        Map<String, Object> consumerConfig = new HashMap<>(kafkaProperties.buildConsumerProperties());
        consumerConfig.putIfAbsent(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        consumerConfig.putIfAbsent(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

        ConsumerFactory<Object, Object> consumerFactory = new DefaultKafkaConsumerFactory<>(consumerConfig);
        ConcurrentKafkaListenerContainerFactory<Object, Object> factory = new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL);
        factory.setBatchListener(false);

        DeadLetterPublishingRecoverer delegate = new DeadLetterPublishingRecoverer(
                kafkaTemplate,
                (record, ex) -> new TopicPartition(record.topic() + dlqSuffix, record.partition())
        );
        ConsumerRecordRecoverer recoverer = (record, ex) -> {
            dlqMetrics.recordDlqSend(record.topic());
            delegate.accept(record, ex);
        };
        // Redis/DB 일시 장애 등은 재시도 후 DLQ; 경량 멱등은 Redis 폴백 없음(복구 후 DLQ 재처리).
        // 파싱/검증 같은 비복구성 오류는 즉시 DLQ로 보낸다.
        DefaultErrorHandler errorHandler = new DefaultErrorHandler(recoverer, new FixedBackOff(500L, 2L));
        errorHandler.addNotRetryableExceptions(IllegalArgumentException.class);
        factory.setCommonErrorHandler(errorHandler);

        return factory;
    }
}
