package com.loopers.config;

import com.loopers.event.EventEnvelope;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.kafka.core.KafkaTemplate;

import static org.mockito.Mockito.mock;

/**
 * 테스트 환경에서 자동으로 KafkaTemplate Mock을 제공.
 */
@AutoConfiguration
public class TestKafkaAutoConfiguration {

    @Bean
    @Primary
    @SuppressWarnings("unchecked")
    public KafkaTemplate<String, EventEnvelope> testKafkaTemplate() {
        return mock(KafkaTemplate.class);
    }
}
