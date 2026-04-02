package com.loopers.application.queue;

import com.loopers.domain.queue.FeatureFlag;
import com.loopers.domain.queue.QueueConstants;
import com.loopers.domain.queue.QueueRepository;
import com.loopers.domain.queue.QueueService;
import com.loopers.domain.queue.QueueTokenRepository;
import com.loopers.domain.queue.QueueTokenService;
import com.loopers.domain.queue.SchedulerLock;
import com.loopers.domain.queue.SchedulerLockRepository;
import com.loopers.infrastructure.queue.FeatureFlagJpaRepository;
import com.loopers.infrastructure.queue.SchedulerLockJpaRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.Set;

import static com.loopers.config.redis.RedisConfig.REDIS_TEMPLATE_MASTER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;

// [통합 테스트 - 처리량 초과]
//
// 테스트 대상: QueueScheduler
// 테스트 유형: 통합 테스트 (Testcontainers Redis + MySQL)
// 테스트 범위: Scheduler -> Service -> Redis/DB
//
// 스케줄러 배치 크기(18명)를 초과하는 요청이 대기열에 쌓여도
// 시스템이 안정적으로 순차 처리하는지 검증한다.
//
// @MockBean으로 백그라운드 @Scheduled 실행을 비활성화하고,
// 테스트용 인스턴스를 직접 생성하여 수동 호출로 정확한 배치 크기를 검증한다.
@SpringBootTest
@DisplayName("QueueScheduler 처리량 초과 통합 테스트")
class QueueSchedulerIntegrationTest {

    // 백그라운드 @Scheduled 실행을 비활성화하기 위해 MockBean으로 대체
    @MockitoBean
    private QueueScheduler queueScheduler;

    @Autowired
    private QueueService queueService;

    @Autowired
    private QueueTokenService queueTokenService;

    @Autowired
    private QueueRepository queueRepository;

    @Autowired
    private QueueTokenRepository queueTokenRepository;

    @Autowired
    private SchedulerLockRepository schedulerLockRepository;

    @Autowired
    private FeatureFlagJpaRepository featureFlagJpaRepository;

    @Autowired
    private SchedulerLockJpaRepository schedulerLockJpaRepository;

    @Autowired
    @Qualifier(REDIS_TEMPLATE_MASTER)
    private RedisTemplate<String, String> redisTemplate;

    // 테스트용 스케줄러 인스턴스 (실제 의존성 주입, @Scheduled 없음)
    private QueueScheduler testScheduler;

    @BeforeEach
    void setUp() {
        cleanupRedis();
        queueService.resetCache();
        featureFlagJpaRepository.save(new FeatureFlag("QUEUE_ENABLED", true));
        schedulerLockJpaRepository.deleteAll();
        schedulerLockJpaRepository.save(new SchedulerLock("QUEUE_SCHEDULER"));
        testScheduler = new QueueScheduler(queueService, queueTokenService, queueRepository, schedulerLockRepository);
    }

    private void cleanupRedis() {
        redisTemplate.delete(QueueConstants.QUEUE_KEY);
        Set<String> tokenKeys = redisTemplate.keys(QueueConstants.TOKEN_KEY_PREFIX + "*");
        if (tokenKeys != null && !tokenKeys.isEmpty()) {
            redisTemplate.delete(tokenKeys);
        }
    }

    @AfterEach
    void tearDown() {
        redisTemplate.delete("waiting-queue");
        Set<String> tokenKeys = redisTemplate.keys("entry-token:*");
        if (tokenKeys != null && !tokenKeys.isEmpty()) {
            redisTemplate.delete(tokenKeys);
        }
        featureFlagJpaRepository.deleteAll();
    }

    private void enterUsers(int count) {
        for (int i = 1; i <= count; i++) {
            queueRepository.enter((long) i, i);
        }
    }

    @Test
    @DisplayName("배치 크기(18명) 초과 시 한 사이클에 18명만 처리하고 나머지는 대기열에 유지된다")
    void processQueue_exceeds_batch_size_processes_only_batch() {
        // given: 50명 대기
        int totalUsers = 50;
        enterUsers(totalUsers);
        assertThat(queueRepository.getTotalCount()).isEqualTo(totalUsers);

        // when: 스케줄러 1회 실행
        testScheduler.processQueue();

        // then: 18명 처리, 32명 잔류
        int batchSize = QueueConstants.BATCH_SIZE;
        assertThat(queueRepository.getTotalCount()).isEqualTo(totalUsers - batchSize);

        // 처리된 18명은 토큰 보유
        for (int i = 1; i <= batchSize; i++) {
            assertThat(queueTokenRepository.hasToken((long) i)).isTrue();
        }

        // 잔류한 32명은 토큰 미보유
        for (int i = batchSize + 1; i <= totalUsers; i++) {
            assertThat(queueTokenRepository.hasToken((long) i)).isFalse();
        }
    }

    @Test
    @DisplayName("여러 사이클 실행 후 전체 대기열이 정상적으로 소화된다")
    void processQueue_multiple_cycles_drains_all() {
        // given: 50명 대기
        int totalUsers = 50;
        enterUsers(totalUsers);

        // when: 배치 크기 기준으로 필요한 사이클 수만큼 실행
        int batchSize = QueueConstants.BATCH_SIZE;
        int requiredCycles = (totalUsers + batchSize - 1) / batchSize; // ceil(50/18) = 3
        for (int i = 0; i < requiredCycles; i++) {
            testScheduler.processQueue();
        }

        // then: 대기열 비어있고, 전원 토큰 보유
        assertThat(queueRepository.getTotalCount()).isEqualTo(0);
        for (int i = 1; i <= totalUsers; i++) {
            assertThat(queueTokenRepository.hasToken((long) i)).isTrue();
        }
    }

    @Test
    @DisplayName("대규모(200명) 대기열도 여러 사이클에 걸쳐 안정적으로 소화된다")
    void processQueue_large_queue_stable() {
        // given: 200명 대기
        int totalUsers = 200;
        enterUsers(totalUsers);

        // when: 전부 소화될 때까지 반복
        int batchSize = QueueConstants.BATCH_SIZE;
        int maxCycles = (totalUsers / batchSize) + 2; // 여유 포함
        for (int cycle = 0; cycle < maxCycles && queueRepository.getTotalCount() > 0; cycle++) {
            testScheduler.processQueue();
        }

        // then: 대기열 완전 소화, 전원 토큰 보유
        assertThat(queueRepository.getTotalCount()).isEqualTo(0);
        for (int i = 1; i <= totalUsers; i++) {
            assertThat(queueTokenRepository.hasToken((long) i)).isTrue();
        }
    }

    @Test
    @DisplayName("토큰 발급 실패 시 원래 score로 대기열에 재삽입되어 미처리 유저보다 앞선 순서를 유지한다")
    void processQueue_failure_reinserts_with_original_score() {
        // given: 20명 대기 (score = userId, 배치 크기 18명 초과)
        enterUsers(20);

        // userId=2 토큰 발급 실패 설정
        QueueTokenService spyTokenService = Mockito.spy(queueTokenService);
        doThrow(new RuntimeException("Redis connection error"))
                .when(spyTokenService).issueToken(2L);

        QueueScheduler failScheduler = new QueueScheduler(
                queueService, spyTokenService, queueRepository, schedulerLockRepository);

        // when: 스케줄러 1회 실행 (18명 처리, userId=2 실패로 재삽입)
        failScheduler.processQueue();

        // then: 대기열에 3명 남아야 한다 (재삽입된 userId=2 + 미처리 userId=19, 20)
        assertThat(queueRepository.getTotalCount()).isEqualTo(3);

        // 재삽입된 userId=2의 rank가 미처리 유저(19, 20)보다 앞서야 한다
        // score=2로 재삽입되었으므로 score=19, 20보다 앞선 rank=0이어야 한다
        assertThat(queueRepository.getRank(2L)).isPresent().hasValue(0L);
        assertThat(queueRepository.getRank(19L)).isPresent();
        assertThat(queueRepository.getRank(20L)).isPresent();
        assertThat(queueRepository.getRank(2L).get()).isLessThan(queueRepository.getRank(19L).get());
        assertThat(queueRepository.getRank(2L).get()).isLessThan(queueRepository.getRank(20L).get());

        // 실패한 유저는 토큰이 없다
        assertThat(queueTokenRepository.hasToken(2L)).isFalse();

        // 성공한 유저들(1, 3~18)은 토큰을 보유한다
        assertThat(queueTokenRepository.hasToken(1L)).isTrue();
        for (int i = 3; i <= 18; i++) {
            assertThat(queueTokenRepository.hasToken((long) i)).isTrue();
        }
    }

    @Test
    @DisplayName("재삽입된 유저는 다음 사이클에서 정상적으로 토큰을 발급받는다")
    void processQueue_reinserted_user_processed_in_next_cycle() {
        // given: 5명 대기
        enterUsers(5);

        // 첫 번째 사이클: userId=3 토큰 발급 실패
        QueueTokenService spyTokenService = Mockito.spy(queueTokenService);
        doThrow(new RuntimeException("Redis connection error"))
                .when(spyTokenService).issueToken(3L);

        QueueScheduler failScheduler = new QueueScheduler(
                queueService, spyTokenService, queueRepository, schedulerLockRepository);
        failScheduler.processQueue();

        // userId=3만 대기열에 남아 있음을 확인
        assertThat(queueRepository.getTotalCount()).isEqualTo(1);
        assertThat(queueRepository.getRank(3L)).isPresent();

        // when: 두 번째 사이클 — 정상 스케줄러로 실행
        testScheduler.processQueue();

        // then: userId=3도 토큰을 발급받아 대기열이 비워진다
        assertThat(queueRepository.getTotalCount()).isEqualTo(0);
        assertThat(queueTokenRepository.hasToken(3L)).isTrue();
    }

    @Test
    @DisplayName("락 만료 후 새 인스턴스가 락을 획득하면, 이전 인스턴스는 해제할 수 없다")
    void release_by_previous_owner_does_not_affect_new_owner() {
        // given: 이전 소유자(instanceA)가 락을 획득
        String instanceA = "aaaaaaaa";
        String instanceB = "bbbbbbbb";
        assertThat(schedulerLockRepository.tryAcquire("QUEUE_SCHEDULER", instanceA, 30)).isTrue();

        // instanceB가 만료된 락을 재획득하는 상황을 시뮬레이션한다.
        // expireSeconds=-1 → expireThreshold = now + 1초 → lockedAt < expireThreshold이 항상 성립
        assertThat(schedulerLockRepository.tryAcquire("QUEUE_SCHEDULER", instanceB, -1)).isTrue();

        // when: 이전 소유자(instanceA)가 락 해제 시도
        schedulerLockRepository.release("QUEUE_SCHEDULER", instanceA);

        // then: 새 소유자(instanceB)의 락은 여전히 유지되어야 한다
        // instanceA로 다시 획득 시도하면 실패해야 한다 (instanceB가 소유 중)
        assertThat(schedulerLockRepository.tryAcquire("QUEUE_SCHEDULER", instanceA, 30)).isFalse();
    }

    @Test
    @DisplayName("배치 크기 미만(5명)이면 전원 한 사이클에 처리된다")
    void processQueue_under_batch_size_processes_all() {
        // given: 5명 대기
        int totalUsers = 5;
        enterUsers(totalUsers);

        // when: 스케줄러 1회 실행
        testScheduler.processQueue();

        // then: 전원 처리 완료
        assertThat(queueRepository.getTotalCount()).isEqualTo(0);
        for (int i = 1; i <= totalUsers; i++) {
            assertThat(queueTokenRepository.hasToken((long) i)).isTrue();
        }
    }
}
