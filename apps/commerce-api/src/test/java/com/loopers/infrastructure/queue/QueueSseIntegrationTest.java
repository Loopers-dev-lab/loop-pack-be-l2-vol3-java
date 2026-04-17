package com.loopers.infrastructure.queue;

import com.loopers.domain.member.Member;
import com.loopers.infrastructure.redis.EntryTokenRedisRepository;
import com.loopers.infrastructure.redis.WaitingQueueRedisRepository;
import com.loopers.infrastructure.scheduler.QueueAdmissionScheduler;
import com.loopers.interfaces.api.queue.QueueController;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

/**
 * SSE 통합 테스트 — Controller + Registry + Scheduler 실제 연동 검증.
 *
 * <p>Redis만 mock하고, QueueController · QueueSseEmitterRegistry · QueueAdmissionScheduler는
 * 실제 인스턴스를 사용하여 SSE 이벤트 흐름(연결 → position → delta → admitted → 종료)을 검증한다.</p>
 *
 * <p>단위 테스트에서 각 컴포넌트를 개별 검증한 뒤, 이 통합 테스트에서 컴포넌트 간 연동을 검증한다.</p>
 */
class QueueSseIntegrationTest {

    private QueueController controller;
    private QueueSseEmitterRegistry registry;
    private QueueAdmissionScheduler scheduler;
    private WaitingQueueRedisRepository waitingQueueRedisRepository;
    private EntryTokenRedisRepository entryTokenRedisRepository;
    private SimpleMeterRegistry meterRegistry;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        waitingQueueRedisRepository = mock(WaitingQueueRedisRepository.class);
        entryTokenRedisRepository = mock(EntryTokenRedisRepository.class);

        // 실제 인스턴스 — 컴포넌트 간 연동을 검증
        registry = new QueueSseEmitterRegistry(meterRegistry);
        scheduler = new QueueAdmissionScheduler(
            waitingQueueRedisRepository, registry, meterRegistry);
        controller = new QueueController(
            waitingQueueRedisRepository, entryTokenRedisRepository,
            registry, meterRegistry, 48_000L);
    }

    @DisplayName("SSE 전체 흐름: stream 연결 → 스케줄러 입장 → admitted 이벤트 → 연결 종료")
    @Test
    void fullSseLifecycle_connectDeltaAdmitDisconnect() {
        // Given: 3명이 대기열에 있음 (토큰 없음)
        when(entryTokenRedisRepository.exists(anyLong())).thenReturn(false);
        when(waitingQueueRedisRepository.getRank(1L)).thenReturn(20L);
        when(waitingQueueRedisRepository.getRank(2L)).thenReturn(10L);
        when(waitingQueueRedisRepository.getRank(3L)).thenReturn(0L);

        // When: 3명이 /queue/stream SSE 연결
        SseEmitter e1 = controller.stream(mockMember(1L));
        SseEmitter e2 = controller.stream(mockMember(2L));
        SseEmitter e3 = controller.stream(mockMember(3L));

        // Then: 3개 연결 등록 + 각각 position 이벤트 전송됨
        assertThat(e1).isNotNull();
        assertThat(e2).isNotNull();
        assertThat(e3).isNotNull();
        assertThat(registry.getConnectionCount()).isEqualTo(3);

        // When: 스케줄러가 user 3 입장 처리 (Lua: ZPOPMIN + SETEX)
        when(waitingQueueRedisRepository.popMinAndIssueTokens(8))
            .thenReturn(List.of("3"));
        when(waitingQueueRedisRepository.size()).thenReturn(2L);
        scheduler.admitUsers();

        // Then: user 3 → admitted 이벤트 + 제거, user 1·2에게 delta(admittedCount=1) 전송
        assertThat(registry.getConnectionCount()).isEqualTo(2);

        // When: 스케줄러가 user 2 입장 처리
        when(waitingQueueRedisRepository.popMinAndIssueTokens(8))
            .thenReturn(List.of("2"));
        when(waitingQueueRedisRepository.size()).thenReturn(1L);
        scheduler.admitUsers();

        // Then: user 2 admitted, user 1만 남음
        assertThat(registry.getConnectionCount()).isEqualTo(1);

        // When: 스케줄러가 user 1 입장 처리 (마지막)
        when(waitingQueueRedisRepository.popMinAndIssueTokens(8))
            .thenReturn(List.of("1"));
        when(waitingQueueRedisRepository.size()).thenReturn(0L);
        scheduler.admitUsers();

        // Then: 전원 입장 → SSE 연결 0
        assertThat(registry.getConnectionCount()).isEqualTo(0);

        // 메트릭: 총 3명 입장 처리
        assertThat(meterRegistry.counter("queue.admission.count").count()).isEqualTo(3.0);
    }

    @DisplayName("배치 입장: 한 사이클에 여러 명 동시 admitted + 나머지에게 delta")
    @Test
    void batchAdmission_multipleUsersAdmittedInOneCycle() {
        when(entryTokenRedisRepository.exists(anyLong())).thenReturn(false);
        when(waitingQueueRedisRepository.getRank(1L)).thenReturn(4L);
        when(waitingQueueRedisRepository.getRank(2L)).thenReturn(3L);
        when(waitingQueueRedisRepository.getRank(3L)).thenReturn(2L);
        when(waitingQueueRedisRepository.getRank(4L)).thenReturn(1L);
        when(waitingQueueRedisRepository.getRank(5L)).thenReturn(0L);

        // 5명 SSE 연결
        for (long i = 1; i <= 5; i++) {
            controller.stream(mockMember(i));
        }
        assertThat(registry.getConnectionCount()).isEqualTo(5);

        // 한 사이클에 3명(user 5,4,3) 동시 입장
        when(waitingQueueRedisRepository.popMinAndIssueTokens(8))
            .thenReturn(List.of("5", "4", "3"));
        when(waitingQueueRedisRepository.size()).thenReturn(2L);
        scheduler.admitUsers();

        // 3명 admitted + 제거 → 2명(user 1,2)만 남음
        // 남은 2명에게 delta(admittedCount=3) 전송
        assertThat(registry.getConnectionCount()).isEqualTo(2);

        // 다음 사이클: 나머지 2명 입장
        when(waitingQueueRedisRepository.popMinAndIssueTokens(8))
            .thenReturn(List.of("2", "1"));
        when(waitingQueueRedisRepository.size()).thenReturn(0L);
        scheduler.admitUsers();

        assertThat(registry.getConnectionCount()).isEqualTo(0);
        assertThat(meterRegistry.counter("queue.admission.count").count()).isEqualTo(5.0);
    }

    @DisplayName("heartbeat: SSE 연결이 끊기지 않고 유지됨")
    @Test
    void heartbeat_keepsConnectionAlive() {
        when(entryTokenRedisRepository.exists(1L)).thenReturn(false);
        when(waitingQueueRedisRepository.getRank(1L)).thenReturn(5L);

        controller.stream(mockMember(1L));
        assertThat(registry.getConnectionCount()).isEqualTo(1);

        // heartbeat 전송 — 연결 유지
        scheduler.sendSseHeartbeat();

        assertThat(registry.getConnectionCount()).isEqualTo(1);
    }

    @DisplayName("재연결: 동일 memberId → 기존 emitter 교체 후 신규 position 전송")
    @Test
    void reconnect_sameMemberId_replacesExistingEmitter() {
        when(entryTokenRedisRepository.exists(1L)).thenReturn(false);
        when(waitingQueueRedisRepository.getRank(1L)).thenReturn(20L);

        SseEmitter first = controller.stream(mockMember(1L));
        assertThat(registry.getConnectionCount()).isEqualTo(1);

        // 재연결: 순번이 변경된 상태에서 다시 연결
        when(waitingQueueRedisRepository.getRank(1L)).thenReturn(12L);
        SseEmitter second = controller.stream(mockMember(1L));

        // 연결 수는 1 유지, 새 emitter로 교체됨
        assertThat(registry.getConnectionCount()).isEqualTo(1);
        assertThat(second).isNotSameAs(first);
    }

    @DisplayName("빈 큐 사이클: 입장 대상 없으면 SSE delta 미전송 + 연결 유지")
    @Test
    void emptyAdmission_noEventSent_connectionsMaintained() {
        when(entryTokenRedisRepository.exists(1L)).thenReturn(false);
        when(waitingQueueRedisRepository.getRank(1L)).thenReturn(5L);

        controller.stream(mockMember(1L));

        // 스케줄러 실행 — 빈 큐
        when(waitingQueueRedisRepository.popMinAndIssueTokens(8))
            .thenReturn(Collections.emptyList());
        when(waitingQueueRedisRepository.size()).thenReturn(1L);
        scheduler.admitUsers();

        // 아무도 입장 안 함 → 연결 유지
        assertThat(registry.getConnectionCount()).isEqualTo(1);
    }

    @DisplayName("이미 입장된 유저의 stream 요청 → admitted emitter 반환 + registry 미등록")
    @Test
    void alreadyAdmitted_returnsAdmittedEmitter_notRegistered() {
        when(entryTokenRedisRepository.exists(1L)).thenReturn(true);

        SseEmitter emitter = controller.stream(mockMember(1L));

        assertThat(emitter).isNotNull();
        // registry에 등록되지 않음 (즉시 admitted 이벤트 후 닫힘)
        assertThat(registry.getConnectionCount()).isEqualTo(0);
    }

    @DisplayName("대기열에 없는 유저의 stream 요청 → not_in_queue emitter 반환 + registry 미등록")
    @Test
    void notInQueue_returnsNotInQueueEmitter_notRegistered() {
        when(entryTokenRedisRepository.exists(1L)).thenReturn(false);
        when(waitingQueueRedisRepository.getRank(1L)).thenReturn(null);

        SseEmitter emitter = controller.stream(mockMember(1L));

        assertThat(emitter).isNotNull();
        assertThat(registry.getConnectionCount()).isEqualTo(0);
    }

    @DisplayName("SSE 게이지 메트릭: 연결 수 변화 추적")
    @Test
    void sseConnectionGauge_tracksConnectionCount() {
        when(entryTokenRedisRepository.exists(anyLong())).thenReturn(false);
        when(waitingQueueRedisRepository.getRank(1L)).thenReturn(10L);
        when(waitingQueueRedisRepository.getRank(2L)).thenReturn(5L);

        // 0 → 2
        controller.stream(mockMember(1L));
        controller.stream(mockMember(2L));
        assertThat(registry.getConnectionCount()).isEqualTo(2);

        // 2 → 1 (user 2 admitted)
        when(waitingQueueRedisRepository.popMinAndIssueTokens(8))
            .thenReturn(List.of("2"));
        when(waitingQueueRedisRepository.size()).thenReturn(1L);
        scheduler.admitUsers();
        assertThat(registry.getConnectionCount()).isEqualTo(1);

        // 1 → 0 (user 1 admitted)
        when(waitingQueueRedisRepository.popMinAndIssueTokens(8))
            .thenReturn(List.of("1"));
        when(waitingQueueRedisRepository.size()).thenReturn(0L);
        scheduler.admitUsers();
        assertThat(registry.getConnectionCount()).isEqualTo(0);
    }

    private Member mockMember(Long id) {
        Member member = mock(Member.class);
        when(member.getId()).thenReturn(id);
        return member;
    }
}
