package com.loopers.confg.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.TopicPartition;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.*;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.converter.BatchMessagingMessageConverter;
import org.springframework.kafka.support.converter.ByteArrayJsonMessageConverter;
import org.springframework.util.backoff.FixedBackOff;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@EnableKafka
@Configuration
@EnableConfigurationProperties(KafkaProperties.class)
public class KafkaConfig {
    public static final String BATCH_LISTENER = "BATCH_LISTENER_DEFAULT";
    public static final String VIEW_BATCH_LISTENER = "VIEW_BATCH_LISTENER";

    public static final int MAX_POLLING_SIZE = 500; // ~132 rps 기준 적정 배치. 리밸런싱 마진 확보
    public static final int FETCH_MIN_BYTES = 1; // 메시지 도착 즉시 반환. 현재 트래픽에서 1MB 대기 시 항상 타임아웃
    public static final int FETCH_MAX_WAIT_MS = 1000; // 1초 대기 후 반환. 소비 지연 최소화
    public static final int SESSION_TIMEOUT_MS = 60 * 1000; // session timeout = 1m
    public static final int HEARTBEAT_INTERVAL_MS = 20 * 1000; // heartbeat interval = 20s ( 1/3 of session_timeout )
    public static final int MAX_POLL_INTERVAL_MS = 2 * 60 * 1000; // max poll interval = 2m

    @Bean
    public ProducerFactory<Object, Object> producerFactory(KafkaProperties kafkaProperties) {
        Map<String, Object> props = new HashMap<>(kafkaProperties.buildProducerProperties());
        return new DefaultKafkaProducerFactory<>(props);
    }

    @Bean
    public ConsumerFactory<Object, Object> consumerFactory(KafkaProperties kafkaProperties) {
        Map<String, Object> props = new HashMap<>(kafkaProperties.buildConsumerProperties());
        return new DefaultKafkaConsumerFactory<>(props);
    }

    @Bean
    public KafkaTemplate<Object, Object> kafkaTemplate(ProducerFactory<Object, Object> producerFactory) {
        return new KafkaTemplate<>(producerFactory);
    }

    @Bean
    public DeadLetterPublishingRecoverer deadLetterPublishingRecoverer(KafkaTemplate<Object, Object> kafkaTemplate) {
        return new DeadLetterPublishingRecoverer(kafkaTemplate, (record, ex) ->
                new TopicPartition(record.topic() + ".DLT", -1));
    }

    @Bean
    public CommonErrorHandler commonErrorHandler(DeadLetterPublishingRecoverer recoverer) {
        DefaultErrorHandler errorHandler = new DefaultErrorHandler(recoverer, new FixedBackOff(1000L, 2L));
        errorHandler.addNotRetryableExceptions(
                com.fasterxml.jackson.core.JsonParseException.class,
                com.fasterxml.jackson.databind.JsonMappingException.class
        );
        return errorHandler;
    }

    @Bean
    public ByteArrayJsonMessageConverter jsonMessageConverter(ObjectMapper objectMapper) {
        return new ByteArrayJsonMessageConverter(objectMapper);
    }

    @Bean(name = BATCH_LISTENER)
    public ConcurrentKafkaListenerContainerFactory<Object, Object> defaultBatchListenerContainerFactory(
            KafkaProperties kafkaProperties,
            ByteArrayJsonMessageConverter converter,
            CommonErrorHandler commonErrorHandler
    ) {
        Map<String, Object> consumerConfig = new HashMap<>(kafkaProperties.buildConsumerProperties());
        consumerConfig.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, MAX_POLLING_SIZE);
        consumerConfig.put(ConsumerConfig.FETCH_MIN_BYTES_CONFIG, FETCH_MIN_BYTES);
        consumerConfig.put(ConsumerConfig.FETCH_MAX_WAIT_MS_CONFIG, FETCH_MAX_WAIT_MS);
        consumerConfig.put(ConsumerConfig.SESSION_TIMEOUT_MS_CONFIG, SESSION_TIMEOUT_MS);
        consumerConfig.put(ConsumerConfig.HEARTBEAT_INTERVAL_MS_CONFIG, HEARTBEAT_INTERVAL_MS);
        consumerConfig.put(ConsumerConfig.MAX_POLL_INTERVAL_MS_CONFIG, MAX_POLL_INTERVAL_MS);

        ConcurrentKafkaListenerContainerFactory<Object, Object> factory = new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(new DefaultKafkaConsumerFactory<>(consumerConfig));
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL); // 수동 커밋
        factory.setBatchMessageConverter(new BatchMessagingMessageConverter(converter));
        factory.setCommonErrorHandler(commonErrorHandler);
        factory.setConcurrency(3);
        factory.setBatchListener(true);
        return factory;
    }

    @Bean(name = VIEW_BATCH_LISTENER)
    public ConcurrentKafkaListenerContainerFactory<Object, Object> viewBatchListenerContainerFactory(
            KafkaProperties kafkaProperties,
            ByteArrayJsonMessageConverter converter,
            CommonErrorHandler commonErrorHandler
    ) {
        Map<String, Object> consumerConfig = new HashMap<>(kafkaProperties.buildConsumerProperties());
        consumerConfig.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, MAX_POLLING_SIZE);
        consumerConfig.put(ConsumerConfig.FETCH_MIN_BYTES_CONFIG, FETCH_MIN_BYTES);
        consumerConfig.put(ConsumerConfig.FETCH_MAX_WAIT_MS_CONFIG, FETCH_MAX_WAIT_MS);
        consumerConfig.put(ConsumerConfig.SESSION_TIMEOUT_MS_CONFIG, SESSION_TIMEOUT_MS);
        consumerConfig.put(ConsumerConfig.HEARTBEAT_INTERVAL_MS_CONFIG, HEARTBEAT_INTERVAL_MS);
        consumerConfig.put(ConsumerConfig.MAX_POLL_INTERVAL_MS_CONFIG, 5 * 60 * 1000); // view는 buffer 패턴이므로 여유 확보
        consumerConfig.put(ConsumerConfig.PARTITION_ASSIGNMENT_STRATEGY_CONFIG,
                "org.apache.kafka.clients.consumer.CooperativeStickyAssignor");
        consumerConfig.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, true);
        consumerConfig.put(ConsumerConfig.AUTO_COMMIT_INTERVAL_MS_CONFIG, 5000);

        ConcurrentKafkaListenerContainerFactory<Object, Object> factory = new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(new DefaultKafkaConsumerFactory<>(consumerConfig));
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.BATCH); // auto commit에 위임
        factory.setBatchMessageConverter(new BatchMessagingMessageConverter(converter));
        factory.setCommonErrorHandler(commonErrorHandler);
        factory.setConcurrency(12);
        factory.setBatchListener(true);
        return factory;
    }
}
