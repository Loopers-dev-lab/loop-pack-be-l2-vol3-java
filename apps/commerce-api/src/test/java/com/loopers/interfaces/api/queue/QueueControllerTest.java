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
        assertThat(data.suggestedPollIntervalMs()).isEqualTo(1000L);
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
        assertThat(data.suggestedPollIntervalMs()).isNull();
        verify(waitingQueueRedisRepository, never()).add(anyLong());
    }

    @DisplayName("enter: 중복 진입 → 기존 순번 유지")
    @Test
    void enter_duplicateEntry_keepsSamePosition() {
        when(entryTokenRedisRepository.exists(1L)).thenReturn(false);
        when(waitingQueueRedisRepository.add(1L)).thenReturn(false);
        when(waitingQueueRedisRepository.getRank(1L)).thenReturn(10L);

        ApiResponse<QueueDto.EnterResponse> response = controller.enter(member);

        QueueDto.EnterResponse data = response.data();
        assertThat(data.status()).isEqualTo("QUEUED");
        assertThat(data.position()).isEqualTo(11L);
    }

    @DisplayName("enter: 대기열 가득 참 → QUEUE_FULL 반환")
    @Test
    void enter_queueFull_returnsQueueFull() {
        when(entryTokenRedisRepository.exists(1L)).thenReturn(false);
        when(waitingQueueRedisRepository.size()).thenReturn(48_000L);

        ApiResponse<QueueDto.EnterResponse> response = controller.enter(member);

        QueueDto.EnterResponse data = response.data();
        assertThat(data.status()).isEqualTo("QUEUE_FULL");
        assertThat(data.position()).isNull();
        assertThat(data.estimatedWaitSeconds()).isNull();
        assertThat(data.tokenRemainingSeconds()).isNull();
        assertThat(data.suggestedPollIntervalMs()).isNull();
        verify(waitingQueueRedisRepository, never()).add(anyLong());
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
        assertThat(data.suggestedPollIntervalMs()).isEqualTo(1000L);
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
        assertThat(data.suggestedPollIntervalMs()).isNull();
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
        assertThat(data.suggestedPollIntervalMs()).isNull();
    }

    // --- 동적 Polling 구간별 검증 ---

    @DisplayName("calculatePollInterval: 1~100 → 1000ms")
    @Test
    void calculatePollInterval_nearFront_returns1000() {
        assertThat(QueueController.calculatePollInterval(1)).isEqualTo(1000L);
        assertThat(QueueController.calculatePollInterval(100)).isEqualTo(1000L);
    }

    @DisplayName("calculatePollInterval: 101~1000 → 3000ms")
    @Test
    void calculatePollInterval_middle_returns3000() {
        assertThat(QueueController.calculatePollInterval(101)).isEqualTo(3000L);
        assertThat(QueueController.calculatePollInterval(1000)).isEqualTo(3000L);
    }

    @DisplayName("calculatePollInterval: 1001+ → 5000ms")
    @Test
    void calculatePollInterval_farBack_returns5000() {
        assertThat(QueueController.calculatePollInterval(1001)).isEqualTo(5000L);
        assertThat(QueueController.calculatePollInterval(48000)).isEqualTo(5000L);
    }
}
