package com.loopers.infrastructure.kafka;

import com.loopers.confg.kafka.KafkaConfig;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

import java.util.HashMap;
import java.util.Map;

@Configuration
@Slf4j
public class KafkaConsumerConfig {

    public static final String SINGLE_LISTENER = "SINGLE_LISTENER_DEFAULT";

    /**
     * DLQ 에러 핸들러.
     * - 3회 재시도 (1초 간격) 후 Dead Letter Topic으로 격리
     * - Dead Letter Topic명: 원본토픽 + ".DLT" (예: catalog-events.DLT)
     * - 독 메시지(파싱 불가 등)가 Consumer를 무한 차단하는 것을 방지
     */
    @Bean
    public CommonErrorHandler kafkaErrorHandler(KafkaTemplate<Object, Object> kafkaTemplate) {
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(kafkaTemplate);
        DefaultErrorHandler errorHandler = new DefaultErrorHandler(recoverer, new FixedBackOff(1000L, 3L));

        // 재시도하지 않을 예외 (바로 DLT로 이동)
        errorHandler.addNotRetryableExceptions(
            com.fasterxml.jackson.core.JsonParseException.class,
            com.fasterxml.jackson.databind.JsonMappingException.class
        );

        return errorHandler;
    }

    /**
     * 배치 Consumer Factory (catalog-events, order-events용).
     * - 배치(List) 수신 + manual ACK
     * - DLQ 에러 핸들러 적용
     */
    @Bean(name = KafkaConfig.BATCH_LISTENER)
    public ConcurrentKafkaListenerContainerFactory<String, String> batchListenerFactory(
            KafkaProperties kafkaProperties,
            CommonErrorHandler kafkaErrorHandler
    ) {
        Map<String, Object> props = new HashMap<>(kafkaProperties.buildConsumerProperties());
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, 100);

        ConcurrentKafkaListenerContainerFactory<String, String> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(new DefaultKafkaConsumerFactory<>(props));
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL);
        factory.setBatchListener(true);
        factory.setConcurrency(1);
        factory.setCommonErrorHandler(kafkaErrorHandler);
        return factory;
    }

    /**
     * 단건 Consumer Factory (coupon-issue-requests용).
     * - 단건(ConsumerRecord) 수신 + manual ACK
     * - DLQ 에러 핸들러 적용
     */
    @Bean(name = SINGLE_LISTENER)
    public ConcurrentKafkaListenerContainerFactory<String, String> singleListenerFactory(
            KafkaProperties kafkaProperties,
            CommonErrorHandler kafkaErrorHandler
    ) {
        Map<String, Object> props = new HashMap<>(kafkaProperties.buildConsumerProperties());
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);

        ConcurrentKafkaListenerContainerFactory<String, String> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(new DefaultKafkaConsumerFactory<>(props));
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL);
        factory.setBatchListener(false);
        factory.setConcurrency(1);
        factory.setCommonErrorHandler(kafkaErrorHandler);
        return factory;
    }
}
