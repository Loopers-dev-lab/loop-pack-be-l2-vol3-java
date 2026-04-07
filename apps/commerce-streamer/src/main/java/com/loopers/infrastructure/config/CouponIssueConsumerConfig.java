package com.loopers.infrastructure.config;

import com.loopers.infrastructure.metrics.KafkaCollectorDlqMetrics;
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
import org.springframework.kafka.listener.ConsumerRecordRecoverer;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

import java.util.HashMap;
import java.util.Map;

/**
 * 쿠폰 발급 요청 토픽 전용: 도메인 이벤트와 동일한 DLQ·재시도 정책.
 */
@Configuration
public class CouponIssueConsumerConfig {

    public static final String COUPON_ISSUE_LISTENER = "COUPON_ISSUE_LISTENER";

    @Bean(name = COUPON_ISSUE_LISTENER)
    public ConcurrentKafkaListenerContainerFactory<Object, Object> couponIssueListenerContainerFactory(
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
        DefaultErrorHandler errorHandler = new DefaultErrorHandler(recoverer, new FixedBackOff(500L, 2L));
        errorHandler.addNotRetryableExceptions(IllegalArgumentException.class);
        factory.setCommonErrorHandler(errorHandler);

        return factory;
    }
}
