package com.loopers.interfaces.consumer.support;

import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;

/**
 * Kafka 메시지 역직렬화 유틸리티.
 *
 * <p>StringSerializer로 발행된 JSON 메시지는 이중 인코딩되어
 * {@code "\"{ ... }\""} 형태로 수신될 수 있다.
 * 이 경우 외부 따옴표를 벗긴 후 역직렬화한다.</p>
 */
@Component
@RequiredArgsConstructor
public class KafkaMessageParser {

    private final ObjectMapper objectMapper;

    public <T> T parse(Object rawValue, Class<T> type) throws Exception {
        String raw = rawValue.toString();
        try {
            return objectMapper.readValue(raw, type);
        } catch (Exception e) {
            // 이중 인코딩된 경우: 외부 따옴표를 벗기고 재시도
            String unwrapped = objectMapper.readValue(raw, String.class);
            return objectMapper.readValue(unwrapped, type);
        }
    }
}
