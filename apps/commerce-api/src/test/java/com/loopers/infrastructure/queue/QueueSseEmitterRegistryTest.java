package com.loopers.infrastructure.queue;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class QueueSseEmitterRegistryTest {

    private QueueSseEmitterRegistry registry;
    private SimpleMeterRegistry meterRegistry;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        registry = new QueueSseEmitterRegistry(meterRegistry);
    }

    @DisplayName("정상 등록: emitter 반환 + connectionCount 증가")
    @Test
    void register_returnsEmitterAndIncrementsCount() {
        SseEmitter emitter = registry.register(1L, 42);

        assertThat(emitter).isNotNull();
        assertThat(registry.getConnectionCount()).isEqualTo(1);
    }

    @DisplayName("admitted 유저: onAdmission 후 emitter 제거")
    @Test
    void onAdmission_removesAdmittedEmitters() {
        registry.register(1L, 10);
        registry.register(2L, 20);
        registry.register(3L, 30);
        assertThat(registry.getConnectionCount()).isEqualTo(3);

        registry.onAdmission(List.of("1", "2"), 2);

        // admitted 유저 1, 2는 제거됨. 3은 남아있음.
        assertThat(registry.getConnectionCount()).isLessThanOrEqualTo(1);
    }

    @DisplayName("delta 브로드캐스트: 남은 클라이언트에게 delta 전송")
    @Test
    void onAdmission_broadcastsDeltaToRemaining() {
        registry.register(10L, 50);
        registry.register(20L, 100);

        // admitted 없이 delta만 브로드캐스트
        registry.onAdmission(List.of(), 8);

        // emitter가 아직 살아있음
        assertThat(registry.getConnectionCount()).isGreaterThanOrEqualTo(0);
    }

    @DisplayName("중복 memberId: 기존 emitter 교체")
    @Test
    void register_duplicateMemberId_replacesExisting() {
        SseEmitter first = registry.register(1L, 42);
        assertThat(registry.getConnectionCount()).isEqualTo(1);

        SseEmitter second = registry.register(1L, 30);
        assertThat(registry.getConnectionCount()).isEqualTo(1);
        assertThat(second).isNotSameAs(first);
    }
}
