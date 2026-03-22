package com.loopers.domain.order;

import java.nio.ByteBuffer;
import java.util.Base64;
import java.util.UUID;

import org.springframework.stereotype.Component;

/**
 * 주문 키(orderKey) 생성기.
 *
 * <p>UUID(128bit)를 URL-safe Base64로 인코딩하여 22자의 고유 키를 생성한다.</p>
 */
@Component
public class OrderKeyGenerator {

    /**
     * 고유한 주문 키를 생성한다.
     *
     * @return URL-safe한 22자 고유 문자열
     */
    public String generate() {
        UUID uuid = UUID.randomUUID();
        byte[] bytes = ByteBuffer.allocate(16)
                .putLong(uuid.getMostSignificantBits())
                .putLong(uuid.getLeastSignificantBits())
                .array();
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
