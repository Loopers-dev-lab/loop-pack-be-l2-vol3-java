package com.loopers.infrastructure.order.keygen;

import java.nio.ByteBuffer;
import java.util.Base64;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.loopers.domain.order.OrderKeyGenerator;

/**
 * UUID 기반 {@link OrderKeyGenerator} 구현체.
 *
 * <p>UUID(128bit)를 URL-safe Base64로 인코딩하여 22자의 고유 키를 생성한다.</p>
 */
@Component
public class UuidOrderKeyGenerator implements OrderKeyGenerator {

    @Override
    public String generate() {
        UUID uuid = UUID.randomUUID();
        byte[] bytes = ByteBuffer.allocate(16)
                .putLong(uuid.getMostSignificantBits())
                .putLong(uuid.getLeastSignificantBits())
                .array();
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
