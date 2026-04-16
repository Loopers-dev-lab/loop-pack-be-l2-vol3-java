package com.loopers.confg.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.kafka.topic.KafkaTopics;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.config.TopicConfig;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.*;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.support.converter.BatchMessagingMessageConverter;
import org.springframework.kafka.support.converter.ByteArrayJsonMessageConverter;

import java.util.HashMap;
import java.util.Map;

@EnableKafka
@Configuration
@EnableConfigurationProperties(KafkaProperties.class)
public class KafkaConfig {
    public static final String BATCH_LISTENER = "BATCH_LISTENER_DEFAULT";

    public static final int MAX_POLLING_SIZE = 3000; // read 3000 msg
    public static final int FETCH_MIN_BYTES = (1024 * 1024); // 1mb
    public static final int FETCH_MAX_WAIT_MS = 5 * 1000; // broker waiting time = 5s
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

    // 토픽은 KafkaAdmin이 기동 시 자동 생성 (없으면 생성, 있으면 스킵)
    @Bean
    @Profile("!cluster")
    public org.apache.kafka.clients.admin.NewTopic catalogEventsTopic() {
        return TopicBuilder.name(KafkaTopics.CATALOG_EVENTS)
            .partitions(3)
            .replicas(1)
            .config(TopicConfig.MIN_IN_SYNC_REPLICAS_CONFIG, "1")
            .build();
    }

    @Bean
    @Profile("!cluster")
    public org.apache.kafka.clients.admin.NewTopic couponIssueRequestsTopic() {
        return TopicBuilder.name(KafkaTopics.COUPON_ISSUE_REQUESTS)
            .partitions(1)  // 쿠폰 발급은 순서 보장 필요 → 파티션 1개
            .replicas(1)
            .config(TopicConfig.MIN_IN_SYNC_REPLICAS_CONFIG, "1")
            .build();
    }

    // cluster 프로파일: 3-broker 클러스터 환경 — replicas=3, min.isr=2
    @Bean
    @Profile("cluster")
    public org.apache.kafka.clients.admin.NewTopic catalogEventsTopicCluster() {
        return TopicBuilder.name(KafkaTopics.CATALOG_EVENTS)
            .partitions(3)
            .replicas(3)
            .config(TopicConfig.MIN_IN_SYNC_REPLICAS_CONFIG, "2")
            .build();
    }

    @Bean
    @Profile("cluster")
    public org.apache.kafka.clients.admin.NewTopic couponIssueRequestsTopicCluster() {
        return TopicBuilder.name(KafkaTopics.COUPON_ISSUE_REQUESTS)
            .partitions(1)
            .replicas(3)
            .config(TopicConfig.MIN_IN_SYNC_REPLICAS_CONFIG, "2")
            .build();
    }

    @Bean
    public ByteArrayJsonMessageConverter jsonMessageConverter(ObjectMapper objectMapper) {
        return new ByteArrayJsonMessageConverter(objectMapper);
    }

    @Bean(name = BATCH_LISTENER)
    public ConcurrentKafkaListenerContainerFactory<Object, Object> defaultBatchListenerContainerFactory(
            KafkaProperties kafkaProperties,
            ByteArrayJsonMessageConverter converter
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
        factory.setConcurrency(3);
        factory.setBatchListener(true);
        return factory;
    }
}
