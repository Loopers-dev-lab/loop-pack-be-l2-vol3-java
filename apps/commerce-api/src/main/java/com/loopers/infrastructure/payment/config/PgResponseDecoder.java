package com.loopers.infrastructure.payment.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import feign.Response;
import feign.codec.Decoder;

import java.io.IOException;
import java.lang.reflect.Type;

public class PgResponseDecoder implements Decoder {

    private final ObjectMapper objectMapper;

    public PgResponseDecoder(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public Object decode(Response response, Type type) throws IOException {
        if (response.body() == null) {
            return null;
        }
        byte[] bytes = response.body().asInputStream().readAllBytes();
        JsonNode root = objectMapper.readTree(bytes);
        JsonNode data = root.get("data");
        if (data != null) {
            return objectMapper.treeToValue(data, objectMapper.constructType(type));
        }
        return objectMapper.readValue(bytes, objectMapper.constructType(type));
    }
}
