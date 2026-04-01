package com.loopers.interfaces.api.queue;

import com.loopers.domain.member.Member;
import com.loopers.infrastructure.redis.EntryTokenRedisRepository;
import com.loopers.infrastructure.redis.WaitingQueueRedisRepository;
import com.loopers.interfaces.api.ApiResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class QueueControllerTest {

    private QueueController controller;
    private WaitingQueueRedisRepository waitingQueueRedisRepository;
    private EntryTokenRedisRepository entryTokenRedisRepository;
    private Member member;

    @BeforeEach
    void setUp() {
        waitingQueueRedisRepository = mock(WaitingQueueRedisRepository.class);
        entryTokenRedisRepository = mock(EntryTokenRedisRepository.class);
        controller = new QueueController(waitingQueueRedisRepository, entryTokenRedisRepository);
        member = mock(Member.class);
        when(member.getId()).thenReturn(1L);
    }

    @DisplayName("enter: 토큰 없음 → ZADD → 순번 반환")
    @Test
    void enter_noToken_returnsQueued() {
        when(entryTokenRedisRepository.exists(1L)).thenReturn(false);
        when(waitingQueueRedisRepository.add(1L)).thenReturn(true);
        when(waitingQueueRedisRepository.getRank(1L)).thenReturn(41L);

        ApiResponse<QueueDto.EnterResponse> response = controller.enter(member);

        QueueDto.EnterResponse data = response.data();
        assertThat(data.status()).isEqualTo("QUEUED");
        assertThat(data.position()).isEqualTo(42L);
        assertThat(data.estimatedWaitSeconds()).isNotNull();
        assertThat(data.tokenRemainingSeconds()).isNull();
    }

    @DisplayName("enter: 토큰 이미 존재 → ADMITTED 반환")
    @Test
    void enter_tokenExists_returnsAdmitted() {
        when(entryTokenRedisRepository.exists(1L)).thenReturn(true);
        when(entryTokenRedisRepository.getRemainingTtl(1L)).thenReturn(285L);

        ApiResponse<QueueDto.EnterResponse> response = controller.enter(member);

        QueueDto.EnterResponse data = response.data();
        assertThat(data.status()).isEqualTo("ADMITTED");
        assertThat(data.position()).isNull();
        assertThat(data.tokenRemainingSeconds()).isEqualTo(285L);
        verify(waitingQueueRedisRepository, never()).add(anyLong());
    }

    @DisplayName("enter: 중복 진입 → 기존 순번 유지")
    @Test
    void enter_duplicateEntry_keepsSamePosition() {
        when(entryTokenRedisRepository.exists(1L)).thenReturn(false);
        when(waitingQueueRedisRepository.add(1L)).thenReturn(false); // 이미 존재
        when(waitingQueueRedisRepository.getRank(1L)).thenReturn(10L);

        ApiResponse<QueueDto.EnterResponse> response = controller.enter(member);

        QueueDto.EnterResponse data = response.data();
        assertThat(data.status()).isEqualTo("QUEUED");
        assertThat(data.position()).isEqualTo(11L);
    }

    @DisplayName("position: 대기 중 → WAITING + 순번")
    @Test
    void position_waiting_returnsWaitingWithPosition() {
        when(entryTokenRedisRepository.exists(1L)).thenReturn(false);
        when(waitingQueueRedisRepository.getRank(1L)).thenReturn(99L);
        when(waitingQueueRedisRepository.size()).thenReturn(1500L);

        ApiResponse<QueueDto.PositionResponse> response = controller.position(member);

        QueueDto.PositionResponse data = response.data();
        assertThat(data.status()).isEqualTo("WAITING");
        assertThat(data.position()).isEqualTo(100L);
        assertThat(data.totalQueueSize()).isEqualTo(1500L);
        assertThat(data.estimatedWaitSeconds()).isNotNull();
    }

    @DisplayName("position: 토큰 존재 → ADMITTED")
    @Test
    void position_admitted_returnsAdmitted() {
        when(entryTokenRedisRepository.exists(1L)).thenReturn(true);
        when(entryTokenRedisRepository.getRemainingTtl(1L)).thenReturn(200L);

        ApiResponse<QueueDto.PositionResponse> response = controller.position(member);

        QueueDto.PositionResponse data = response.data();
        assertThat(data.status()).isEqualTo("ADMITTED");
        assertThat(data.tokenRemainingSeconds()).isEqualTo(200L);
    }

    @DisplayName("position: 큐에 없음 → NOT_IN_QUEUE")
    @Test
    void position_notInQueue_returnsNotInQueue() {
        when(entryTokenRedisRepository.exists(1L)).thenReturn(false);
        when(waitingQueueRedisRepository.getRank(1L)).thenReturn(null);

        ApiResponse<QueueDto.PositionResponse> response = controller.position(member);

        QueueDto.PositionResponse data = response.data();
        assertThat(data.status()).isEqualTo("NOT_IN_QUEUE");
        assertThat(data.position()).isNull();
    }
}
