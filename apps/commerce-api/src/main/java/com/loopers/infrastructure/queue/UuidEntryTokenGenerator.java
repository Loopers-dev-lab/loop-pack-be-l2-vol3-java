package com.loopers.infrastructure.queue;

import com.loopers.domain.queue.EntryTokenGenerator;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * 입장(대기열 통과) 자격 토큰을 UUID로 생성한다.
 */
@Component
public class UuidEntryTokenGenerator implements EntryTokenGenerator {

    @Override
    public String generate() {
        return UUID.randomUUID().toString();
    }
}

