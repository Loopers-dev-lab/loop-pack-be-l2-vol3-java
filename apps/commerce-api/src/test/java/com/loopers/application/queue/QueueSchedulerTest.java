package com.loopers.application.queue;

import com.loopers.domain.queue.QueueEntry;
import com.loopers.domain.queue.QueueRepository;
import com.loopers.domain.queue.QueueService;
import com.loopers.domain.queue.QueueTokenService;
import com.loopers.domain.queue.SchedulerLockRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// [단위 테스트 - Application Layer]
//
// 테스트 대상: QueueScheduler
// 테스트 유형: 단위 테스트 (Mock: QueueService, QueueTokenService, QueueRepository, SchedulerLockRepository)
// 테스트 범위: 스케줄러 배치 처리 로직
//
// processQueue 실행:
//   - ZPOPMIN으로 배치 크기(18명)만큼 꺼내 토큰 발급
//   - 대기열이 배치 크기 미만이면 있는 만큼만 처리
//   - 대기열이 비어있으면 아무 일도 하지 않음
//   - Feature Flag OFF 시 스케줄러 미실행
//   - 이미 토큰 보유 유저는 발급 스킵
//   - 토큰 발급 실패 시 원래 score로 대기열 재삽입 (보상 로직)
//   - 한 명의 실패가 나머지 유저 처리에 영향을 주지 않음
//   - 분산 락: 다른 인스턴스 실행 중이면 스킵, 처리 완료/예외 시 락 해제
@ExtendWith(MockitoExtension.class)
@DisplayName("QueueScheduler 단위 테스트")
class QueueSchedulerTest {

    @Mock
    private QueueService queueService;

    @Mock
    private QueueTokenService queueTokenService;

    @Mock
    private QueueRepository queueRepository;

    @Mock
    private SchedulerLockRepository schedulerLockRepository;

    @InjectMocks
    private QueueScheduler queueScheduler;

    private void givenLockAcquired() {
        when(schedulerLockRepository.tryAcquire(eq("QUEUE_SCHEDULER"), anyString(), eq(30L))).thenReturn(true);
    }

    private List<QueueEntry> createEntries(int count) {
        return java.util.stream.IntStream.rangeClosed(1, count)
                .mapToObj(i -> new QueueEntry((long) i, (double) i))
                .toList();
    }

    @Nested
    @DisplayName("processQueue 실행")
    class ProcessQueue {

        @Test
        @DisplayName("성공 - 대기열에서 ZPOPMIN으로 18명을 꺼내 토큰 발급한다")
        void processQueue_pops_batch_size() {
            // given
            when(queueService.isQueueEnabled()).thenReturn(true);
            givenLockAcquired();
            List<QueueEntry> entries = createEntries(18);
            when(queueRepository.popFront(18)).thenReturn(entries);
            when(queueTokenService.hasToken(anyLong())).thenReturn(false);
            when(queueTokenService.issueToken(anyLong())).thenReturn(Optional.of("token"));

            // when
            queueScheduler.processQueue();

            // then
            verify(queueRepository).popFront(18);
            verify(queueTokenService, times(18)).issueToken(anyLong());
        }

        @Test
        @DisplayName("성공 - 대기열에 5명만 있으면 5명만 토큰 발급한다")
        void processQueue_less_than_batch() {
            // given
            when(queueService.isQueueEnabled()).thenReturn(true);
            givenLockAcquired();
            List<QueueEntry> entries = createEntries(5);
            when(queueRepository.popFront(18)).thenReturn(entries);
            when(queueTokenService.hasToken(anyLong())).thenReturn(false);
            when(queueTokenService.issueToken(anyLong())).thenReturn(Optional.of("token"));

            // when
            queueScheduler.processQueue();

            // then
            verify(queueTokenService, times(5)).issueToken(anyLong());
        }

        @Test
        @DisplayName("성공 - 대기열이 비어있으면 아무 일도 하지 않는다")
        void processQueue_empty_queue() {
            // given
            when(queueService.isQueueEnabled()).thenReturn(true);
            givenLockAcquired();
            when(queueRepository.popFront(18)).thenReturn(List.of());

            // when
            queueScheduler.processQueue();

            // then
            verify(queueTokenService, never()).issueToken(anyLong());
        }

        @Test
        @DisplayName("성공 - feature flag OFF이면 스케줄러가 실행되지 않는다")
        void processQueue_flag_off() {
            // given
            when(queueService.isQueueEnabled()).thenReturn(false);

            // when
            queueScheduler.processQueue();

            // then
            verify(queueRepository, never()).popFront(18);
            verify(queueTokenService, never()).issueToken(anyLong());
        }

        @Test
        @DisplayName("성공 - 이미 토큰이 있는 유저는 발급 스킵한다 (ZPOPMIN으로 이미 제거됨)")
        void processQueue_skip_user_with_existing_token() {
            // given
            when(queueService.isQueueEnabled()).thenReturn(true);
            givenLockAcquired();
            List<QueueEntry> entries = createEntries(3);
            when(queueRepository.popFront(18)).thenReturn(entries);
            when(queueTokenService.hasToken(1L)).thenReturn(true);  // 이미 토큰 있음
            when(queueTokenService.hasToken(2L)).thenReturn(false);
            when(queueTokenService.hasToken(3L)).thenReturn(false);
            when(queueTokenService.issueToken(2L)).thenReturn(Optional.of("token-2"));
            when(queueTokenService.issueToken(3L)).thenReturn(Optional.of("token-3"));

            // when
            queueScheduler.processQueue();

            // then
            verify(queueTokenService, never()).issueToken(1L);
            verify(queueTokenService).issueToken(2L);
            verify(queueTokenService).issueToken(3L);
        }

        @Test
        @DisplayName("성공 - 전원이 이미 토큰을 갖고 있으면 발급 없이 완료한다")
        void processQueue_all_have_tokens() {
            // given
            when(queueService.isQueueEnabled()).thenReturn(true);
            givenLockAcquired();
            List<QueueEntry> entries = createEntries(3);
            when(queueRepository.popFront(18)).thenReturn(entries);
            when(queueTokenService.hasToken(anyLong())).thenReturn(true);

            // when
            queueScheduler.processQueue();

            // then
            verify(queueTokenService, never()).issueToken(anyLong());
        }

        @Test
        @DisplayName("성공 - 토큰 발급 실패 시 원래 score로 대기열에 재삽입한다")
        void processQueue_failure_reinserts_with_original_score() {
            // given
            when(queueService.isQueueEnabled()).thenReturn(true);
            givenLockAcquired();
            List<QueueEntry> entries = List.of(
                    new QueueEntry(1L, 1000.0),
                    new QueueEntry(2L, 2000.0),
                    new QueueEntry(3L, 3000.0));
            when(queueRepository.popFront(18)).thenReturn(entries);
            when(queueTokenService.hasToken(anyLong())).thenReturn(false);
            when(queueTokenService.issueToken(1L)).thenThrow(new RuntimeException("Redis error"));
            when(queueTokenService.issueToken(2L)).thenReturn(Optional.of("token-2"));
            when(queueTokenService.issueToken(3L)).thenReturn(Optional.of("token-3"));

            // when
            queueScheduler.processQueue();

            // then: 실패한 유저는 원래 score로 재삽입
            verify(queueRepository).enter(1L, 1000.0);
            // 성공한 유저는 재삽입하지 않음
            verify(queueRepository, never()).enter(eq(2L), anyDouble());
            verify(queueRepository, never()).enter(eq(3L), anyDouble());
        }

        @Test
        @DisplayName("성공 - 한 명의 실패가 나머지 유저 처리에 영향을 주지 않는다")
        void processQueue_failure_does_not_block_others() {
            // given
            when(queueService.isQueueEnabled()).thenReturn(true);
            givenLockAcquired();
            List<QueueEntry> entries = createEntries(3);
            when(queueRepository.popFront(18)).thenReturn(entries);
            when(queueTokenService.hasToken(anyLong())).thenReturn(false);
            when(queueTokenService.issueToken(1L)).thenThrow(new RuntimeException("Redis error"));
            when(queueTokenService.issueToken(2L)).thenReturn(Optional.of("token-2"));
            when(queueTokenService.issueToken(3L)).thenReturn(Optional.of("token-3"));

            // when
            queueScheduler.processQueue();

            // then (2L, 3L은 정상 처리)
            verify(queueTokenService).issueToken(2L);
            verify(queueTokenService).issueToken(3L);
        }

        @Test
        @DisplayName("성공 - 다른 인스턴스가 실행 중이면 이번 주기를 스킵한다")
        void processQueue_lock_not_acquired_skips() {
            // given
            when(queueService.isQueueEnabled()).thenReturn(true);
            when(schedulerLockRepository.tryAcquire(eq("QUEUE_SCHEDULER"), anyString(), eq(30L))).thenReturn(false);

            // when
            queueScheduler.processQueue();

            // then
            verify(queueRepository, never()).popFront(18);
            verify(queueTokenService, never()).issueToken(anyLong());
        }

        @Test
        @DisplayName("성공 - 처리 완료 후 락을 해제한다")
        void processQueue_releases_lock_after_processing() {
            // given
            when(queueService.isQueueEnabled()).thenReturn(true);
            givenLockAcquired();
            when(queueRepository.popFront(18)).thenReturn(List.of(new QueueEntry(1L, 1000.0)));
            when(queueTokenService.hasToken(1L)).thenReturn(false);
            when(queueTokenService.issueToken(1L)).thenReturn(Optional.of("token"));

            // when
            queueScheduler.processQueue();

            // then
            verify(schedulerLockRepository).release("QUEUE_SCHEDULER");
        }

        @Test
        @DisplayName("성공 - 처리 중 예외가 발생해도 락을 해제한다")
        void processQueue_releases_lock_on_exception() {
            // given
            when(queueService.isQueueEnabled()).thenReturn(true);
            givenLockAcquired();
            when(queueRepository.popFront(18)).thenThrow(new RuntimeException("Redis error"));

            // when
            try {
                queueScheduler.processQueue();
            } catch (Exception ignored) {
            }

            // then
            verify(schedulerLockRepository).release("QUEUE_SCHEDULER");
        }
    }
}

