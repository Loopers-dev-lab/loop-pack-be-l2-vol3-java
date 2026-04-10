package com.loopers.support.util;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;

public class KafkaMessageUtil {

    public static <T> T readValue(ObjectMapper objectMapper, Object value, Class<T> clazz) {
        try {
            String json;
            if (value instanceof byte[]) {
                json = new String((byte[]) value, StandardCharsets.UTF_8);
            } else if (value instanceof String) {
                json = (String) value;
            } else {
                json = objectMapper.writeValueAsString(value);
            }

            // JsonSerializer가 String payload를 이중 인코딩한 경우 unwrap
            if (json.startsWith("\"") && json.endsWith("\"")) {
                json = objectMapper.readValue(json, String.class);
            }

            return objectMapper.readValue(json, clazz);
        } catch (Exception e) {
            throw new RuntimeException("메시지 역직렬화 실패", e);
        }
    }
}
