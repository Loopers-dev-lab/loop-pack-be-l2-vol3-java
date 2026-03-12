package com.loopers.interfaces.api.common;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

/**
 * 커서 인코딩/디코딩 유틸리티
 *
 * 커서 데이터를 Base64 URL-safe 문자열로 인코딩/디코딩한다.
 * API 계약의 일부이므로 Interfaces 레이어에 위치한다.
 */
public final class CursorEncoder {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private CursorEncoder() {}

    public static String encode(Map<String, Object> data) {
        try {
            String json = MAPPER.writeValueAsString(data);
            return Base64.getUrlEncoder().withoutPadding().encodeToString(json.getBytes(StandardCharsets.UTF_8));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("커서 인코딩 실패", e);
        }
    }

    public static Map<String, Object> decode(String cursor) {
        try {
            String json = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
            return MAPPER.readValue(json, MAPPER.getTypeFactory().constructMapType(Map.class, String.class, Object.class));
        } catch (Exception e) {
            throw new IllegalArgumentException("유효하지 않은 커서입니다.", e);
        }
    }
}
