package com.loopers.domain.queue;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// [단위 테스트 - Domain Service]
//
// 테스트 대상: QueueService
// 테스트 유형: 단위 테스트 (Mock: QueueRepository, FeatureFlagRepository, QueueTokenService)
// 테스트 범위: 대기열 비즈니스 로직
//
// 대기열 진입 (enter):
//   - ZADD NX 기반 진입 및 중복 진입 시 기존 순번 반환
//   - Feature Flag OFF/미존재 시 진입 차단
//
// 순번 조회 (getPosition):
//   - 대기 중 유저: rank → position 변환, 예상 대기 시간, polling 주기 반환
//   - 토큰 발급 유저: 즉시 입장 가능 응답 (position=0, token 포함)
//   - 대기열 미존재 유저: NOT_FOUND 예외
//   - polling 주기 경계값 검증 (100/101, 1000/1001)
//
// Feature Flag 조회 (isQueueEnabled):
//   - ON/OFF/미존재 시 반환값 검증
//   - 캐시 TTL 이내 재호출 시 DB 미조회 검증
@ExtendWith(MockitoExtension.class)
@DisplayName("QueueService 단위 테스트")
class QueueServiceTest {

    @Mock
    private QueueRepository queueRepository;

    @Mock
    private FeatureFlagRepository featureFlagRepository;

    @Mock
    private QueueTokenService queueTokenService;

    @InjectMocks
    private QueueService queueService;

    private void givenQueueEnabled() {
        FeatureFlag flag = new FeatureFlag("QUEUE_ENABLED", true);
        when(featureFlagRepository.findByFeatureKey("QUEUE_ENABLED")).thenReturn(Optional.of(flag));
    }

    private void givenQueueDisabled() {
        FeatureFlag flag = new FeatureFlag("QUEUE_ENABLED", false);
        when(featureFlagRepository.findByFeatureKey("QUEUE_ENABLED")).thenReturn(Optional.of(flag));
    }

    private void givenQueueFlagNotExists() {
        when(featureFlagRepository.findByFeatureKey("QUEUE_ENABLED")).thenReturn(Optional.empty());
    }

    @Nested
    @DisplayName("대기열 진입")
    class Enter {

        @Test
        @DisplayName("성공 - 대기열에 진입하면 순번을 반환한다")
        void enter_success() {
            // given
            givenQueueEnabled();
            Long userId = 1L;
            when(queueTokenService.hasToken(userId)).thenReturn(false);
            when(queueRepository.enter(eq(userId), anyDouble())).thenReturn(true);
            when(queueRepository.getRank(userId)).thenReturn(Optional.of(0L));

            // when
            long position = queueService.enter(userId);

            // then (rank=0 → 1-based position=1)
            assertThat(position).isEqualTo(1L);
            verify(queueRepository).enter(eq(userId), anyDouble());
        }

        @Test
        @DisplayName("성공 - 동일 유저 중복 진입 시 기존 순번을 반환한다")
        void enter_duplicate_returns_existing_position() {
            // given
            givenQueueEnabled();
            Long userId = 1L;
            when(queueTokenService.hasToken(userId)).thenReturn(false);
            when(queueRepository.enter(eq(userId), anyDouble())).thenReturn(false);
            when(queueRepository.getRank(userId)).thenReturn(Optional.of(5L));

            // when
            long position = queueService.enter(userId);

            // then (rank=5 → 1-based position=6)
            assertThat(position).isEqualTo(6L);
        }

        @Test
        @DisplayName("성공 - 이미 토큰을 보유한 유저는 대기열에 삽입하지 않고 position 0을 반환한다")
        void enter_with_active_token_returns_zero() {
            // given
            givenQueueEnabled();
            Long userId = 1L;
            when(queueTokenService.hasToken(userId)).thenReturn(true);

            // when
            long position = queueService.enter(userId);

            // then
            assertThat(position).isEqualTo(0L);
            verify(queueRepository, never()).enter(eq(userId), anyDouble());
        }

        @Test
        @DisplayName("실패 - feature flag OFF 시 대기열 진입 불가")
        void enter_when_queue_disabled() {
            // given
            givenQueueDisabled();
            Long userId = 1L;

            // when
            CoreException exception = assertThrows(CoreException.class,
                    () -> queueService.enter(userId));

            // then
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
            verify(queueRepository, never()).enter(eq(userId), anyDouble());
        }

        @Test
        @DisplayName("실패 - feature flag가 존재하지 않으면 OFF로 간주하여 진입 불가")
        void enter_when_flag_not_exists() {
            // given
            givenQueueFlagNotExists();
            Long userId = 1L;

            // when
            CoreException exception = assertThrows(CoreException.class,
                    () -> queueService.enter(userId));

            // then
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
            verify(queueRepository, never()).enter(eq(userId), anyDouble());
        }
    }

    @Nested
    @DisplayName("순번 조회")
    class GetPosition {

        @Test
        @DisplayName("성공 - 대기 중인 유저의 순번과 예상 대기 시간을 반환한다")
        void getPosition_waiting_user() {
            // given
            givenQueueEnabled();
            Long userId = 1L;
            when(queueTokenService.findToken(userId)).thenReturn(Optional.empty());
            when(queueRepository.getRank(userId)).thenReturn(Optional.of(175L));

            // when
            QueuePositionInfo info = queueService.getPosition(userId);

            // then (rank=175 → 1-based position=176)
            assertThat(info.position()).isEqualTo(176L);
            assertThat(info.estimatedWaitSeconds()).isEqualTo(1L); // 176 / 18 * 0.1 ≈ 1초
            assertThat(info.token()).isNull();
        }

        @Test
        @DisplayName("성공 - rank 0인 유저(맨 앞)의 position은 1이고 예상 대기 시간은 0이다")
        void getPosition_front_of_queue() {
            // given
            givenQueueEnabled();
            Long userId = 1L;
            when(queueTokenService.findToken(userId)).thenReturn(Optional.empty());
            when(queueRepository.getRank(userId)).thenReturn(Optional.of(0L));

            // when
            QueuePositionInfo info = queueService.getPosition(userId);

            // then (rank=0 → 1-based position=1)
            assertThat(info.position()).isEqualTo(1L);
            assertThat(info.estimatedWaitSeconds()).isEqualTo(0L);
            assertThat(info.token()).isNull();
        }

        @Test
        @DisplayName("성공 - 토큰이 발급된 유저는 즉시 입장 가능 응답을 반환한다")
        void getPosition_token_issued_user() {
            // given
            givenQueueEnabled();
            Long userId = 1L;
            String token = "test-token-uuid";
            when(queueTokenService.findToken(userId)).thenReturn(Optional.of(token));

            // when
            QueuePositionInfo info = queueService.getPosition(userId);

            // then
            assertThat(info.position()).isEqualTo(0L);
            assertThat(info.estimatedWaitSeconds()).isEqualTo(0L);
            assertThat(info.token()).isEqualTo(token);
            assertThat(info.pollIntervalSeconds()).isEqualTo(0);
        }

        @Test
        @DisplayName("실패 - 대기열에 없는 유저 조회 시 예외 발생")
        void getPosition_user_not_in_queue() {
            // given
            givenQueueEnabled();
            Long userId = 999L;
            when(queueTokenService.findToken(userId)).thenReturn(Optional.empty());
            when(queueRepository.getRank(userId)).thenReturn(Optional.empty());

            // when
            CoreException exception = assertThrows(CoreException.class,
                    () -> queueService.getPosition(userId));

            // then
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }

        @Test
        @DisplayName("성공 - 예상 대기 시간 계산이 정확하다 (position / batchSize * intervalMs)")
        void getPosition_estimated_wait_calculation() {
            // given
            givenQueueEnabled();
            Long userId = 1L;
            when(queueTokenService.findToken(userId)).thenReturn(Optional.empty());
            when(queueRepository.getRank(userId)).thenReturn(Optional.of(350L));

            // when
            QueuePositionInfo info = queueService.getPosition(userId);

            // then (rank=350 → 1-based position=351)
            assertThat(info.position()).isEqualTo(351L);
            assertThat(info.estimatedWaitSeconds()).isEqualTo(2L); // 351 / 18 * 0.1 ≈ 1.95 → 2초
        }

        @Test
        @DisplayName("성공 - 순번 1~100은 polling 주기 1초")
        void getPosition_poll_interval_short() {
            // given
            givenQueueEnabled();
            Long userId = 1L;
            when(queueTokenService.findToken(userId)).thenReturn(Optional.empty());
            when(queueRepository.getRank(userId)).thenReturn(Optional.of(49L)); // position=50

            // when
            QueuePositionInfo info = queueService.getPosition(userId);

            // then
            assertThat(info.position()).isEqualTo(50L);
            assertThat(info.pollIntervalSeconds()).isEqualTo(1);
        }

        @Test
        @DisplayName("성공 - 순번 101~1000은 polling 주기 3초")
        void getPosition_poll_interval_medium() {
            // given
            givenQueueEnabled();
            Long userId = 1L;
            when(queueTokenService.findToken(userId)).thenReturn(Optional.empty());
            when(queueRepository.getRank(userId)).thenReturn(Optional.of(499L)); // position=500

            // when
            QueuePositionInfo info = queueService.getPosition(userId);

            // then
            assertThat(info.position()).isEqualTo(500L);
            assertThat(info.pollIntervalSeconds()).isEqualTo(3);
        }

        @Test
        @DisplayName("성공 - 순번 1001 이상은 polling 주기 5초")
        void getPosition_poll_interval_long() {
            // given
            givenQueueEnabled();
            Long userId = 1L;
            when(queueTokenService.findToken(userId)).thenReturn(Optional.empty());
            when(queueRepository.getRank(userId)).thenReturn(Optional.of(1999L)); // position=2000

            // when
            QueuePositionInfo info = queueService.getPosition(userId);

            // then
            assertThat(info.position()).isEqualTo(2000L);
            assertThat(info.pollIntervalSeconds()).isEqualTo(5);
        }

        @Test
        @DisplayName("성공 - 순번 100은 polling 주기 1초 (경계값)")
        void getPosition_poll_interval_boundary_100() {
            // given
            givenQueueEnabled();
            Long userId = 1L;
            when(queueTokenService.findToken(userId)).thenReturn(Optional.empty());
            when(queueRepository.getRank(userId)).thenReturn(Optional.of(99L)); // position=100

            // when
            QueuePositionInfo info = queueService.getPosition(userId);

            // then
            assertThat(info.position()).isEqualTo(100L);
            assertThat(info.pollIntervalSeconds()).isEqualTo(1);
        }

        @Test
        @DisplayName("성공 - 순번 101은 polling 주기 3초 (경계값)")
        void getPosition_poll_interval_boundary_101() {
            // given
            givenQueueEnabled();
            Long userId = 1L;
            when(queueTokenService.findToken(userId)).thenReturn(Optional.empty());
            when(queueRepository.getRank(userId)).thenReturn(Optional.of(100L)); // position=101

            // when
            QueuePositionInfo info = queueService.getPosition(userId);

            // then
            assertThat(info.position()).isEqualTo(101L);
            assertThat(info.pollIntervalSeconds()).isEqualTo(3);
        }

        @Test
        @DisplayName("성공 - 순번 1000은 polling 주기 3초 (경계값)")
        void getPosition_poll_interval_boundary_1000() {
            // given
            givenQueueEnabled();
            Long userId = 1L;
            when(queueTokenService.findToken(userId)).thenReturn(Optional.empty());
            when(queueRepository.getRank(userId)).thenReturn(Optional.of(999L)); // position=1000

            // when
            QueuePositionInfo info = queueService.getPosition(userId);

            // then
            assertThat(info.position()).isEqualTo(1000L);
            assertThat(info.pollIntervalSeconds()).isEqualTo(3);
        }

        @Test
        @DisplayName("성공 - 순번 1001은 polling 주기 5초 (경계값)")
        void getPosition_poll_interval_boundary_1001() {
            // given
            givenQueueEnabled();
            Long userId = 1L;
            when(queueTokenService.findToken(userId)).thenReturn(Optional.empty());
            when(queueRepository.getRank(userId)).thenReturn(Optional.of(1000L)); // position=1001

            // when
            QueuePositionInfo info = queueService.getPosition(userId);

            // then
            assertThat(info.position()).isEqualTo(1001L);
            assertThat(info.pollIntervalSeconds()).isEqualTo(5);
        }

        @Test
        @DisplayName("실패 - feature flag OFF 시 순번 조회 불가")
        void getPosition_when_queue_disabled() {
            // given
            givenQueueDisabled();
            Long userId = 1L;

            // when
            CoreException exception = assertThrows(CoreException.class,
                    () -> queueService.getPosition(userId));

            // then
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @Nested
    @DisplayName("Feature Flag 조회")
    class IsQueueEnabled {

        @Test
        @DisplayName("flag ON이면 true를 반환한다")
        void isQueueEnabled_true() {
            // given
            givenQueueEnabled();

            // when
            boolean result = queueService.isQueueEnabled();

            // then
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("flag OFF이면 false를 반환한다")
        void isQueueEnabled_false() {
            // given
            givenQueueDisabled();

            // when
            boolean result = queueService.isQueueEnabled();

            // then
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("flag가 존재하지 않으면 false를 반환한다 (안전한 기본값)")
        void isQueueEnabled_not_exists_returns_false() {
            // given
            givenQueueFlagNotExists();

            // when
            boolean result = queueService.isQueueEnabled();

            // then
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("캐시 TTL 이내에 재호출하면 DB를 다시 조회하지 않는다")
        void isQueueEnabled_uses_cache_within_ttl() {
            // given
            givenQueueEnabled();

            // when: 2번 연속 호출
            queueService.isQueueEnabled();
            queueService.isQueueEnabled();

            // then: DB 조회는 1번만 발생
            verify(featureFlagRepository).findByFeatureKey("QUEUE_ENABLED");
        }
    }

    @Nested
    @DisplayName("전체 대기 인원 조회")
    class GetTotalCount {

        @Test
        @DisplayName("전체 대기 인원을 반환한다")
        void getTotalCount_returns_count() {
            // given
            when(queueRepository.getTotalCount()).thenReturn(100L);

            // when
            long count = queueService.getTotalCount();

            // then
            assertThat(count).isEqualTo(100L);
        }

        @Test
        @DisplayName("대기열이 비어있으면 0을 반환한다")
        void getTotalCount_empty_queue() {
            // given
            when(queueRepository.getTotalCount()).thenReturn(0L);

            // when
            long count = queueService.getTotalCount();

            // then
            assertThat(count).isEqualTo(0L);
        }
    }
}
