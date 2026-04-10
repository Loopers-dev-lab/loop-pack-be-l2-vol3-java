package com.loopers.infrastructure.queue;

import com.loopers.domain.queue.QueueEntry;
import com.loopers.domain.queue.QueueRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("QueueRepositoryImpl Redis 통합 테스트")
@SpringBootTest
@ActiveProfiles("test")
class QueueRepositoryImplTest {

    @Autowired
    private QueueRepository queueRepository;

    @Autowired
    @Qualifier("redisTemplateMaster")
    private RedisTemplate<String, String> redisTemplate;

    private static final String QUEUE_KEY = "order:waiting-queue";

    @BeforeEach
    void setUp() {
        // 테스트 전 대기열 초기화
        redisTemplate.delete(QUEUE_KEY);
    }

    @AfterEach
    void tearDown() {
        redisTemplate.delete(QUEUE_KEY);
    }

    // ============================
    // addIfAbsent
    // ============================

    @Test
    @DisplayName("신규 유저 추가 시 true를 반환한다")
    void addIfAbsent_NewUser_ShouldReturnTrue() {
        // when
        boolean added = queueRepository.addIfAbsent(1L, 1000.0);

        // then
        assertThat(added).isTrue();
        assertThat(queueRepository.getSize()).isEqualTo(1);
    }

    @Test
    @DisplayName("동일 유저 중복 추가 시 false를 반환하고 기존 score를 유지한다")
    void addIfAbsent_Duplicate_ShouldReturnFalseAndKeepOriginalScore() {
        // given
        queueRepository.addIfAbsent(1L, 1000.0);

        // when — 다른 score로 재시도
        boolean added = queueRepository.addIfAbsent(1L, 9999.0);

        // then
        assertThat(added).isFalse();
        assertThat(queueRepository.getSize()).isEqualTo(1);
        assertThat(queueRepository.getRank(1L)).isEqualTo(0L); // 순번 변경 없음
    }

    // ============================
    // getRank
    // ============================

    @Test
    @DisplayName("존재하는 유저의 순번을 반환한다")
    void getRank_ExistingUser_ShouldReturnPosition() {
        // given — score 순서: userId=1(1000) < userId=2(2000) < userId=3(3000)
        queueRepository.addIfAbsent(1L, 1000.0);
        queueRepository.addIfAbsent(2L, 2000.0);
        queueRepository.addIfAbsent(3L, 3000.0);

        // when & then — 0-based 순번
        assertThat(queueRepository.getRank(1L)).isEqualTo(0L);
        assertThat(queueRepository.getRank(2L)).isEqualTo(1L);
        assertThat(queueRepository.getRank(3L)).isEqualTo(2L);
    }

    @Test
    @DisplayName("미등록 유저 조회 시 null을 반환한다")
    void getRank_NonExistent_ShouldReturnNull() {
        assertThat(queueRepository.getRank(999L)).isNull();
    }

    // ============================
    // getSize
    // ============================

    @Test
    @DisplayName("빈 대기열의 크기는 0이다")
    void getSize_EmptyQueue_ShouldReturnZero() {
        assertThat(queueRepository.getSize()).isEqualTo(0);
    }

    @Test
    @DisplayName("3명 추가 후 크기는 3이다")
    void getSize_AfterAdd_ShouldReturnCorrectCount() {
        // given
        queueRepository.addIfAbsent(1L, 1000.0);
        queueRepository.addIfAbsent(2L, 2000.0);
        queueRepository.addIfAbsent(3L, 3000.0);

        // then
        assertThat(queueRepository.getSize()).isEqualTo(3);
    }

    // ============================
    // popMin
    // ============================

    @Test
    @DisplayName("ZPOPMIN은 score가 가장 작은 유저부터 꺼낸다 (선입선출)")
    void popMin_ShouldReturnEarliestUsers() {
        // given — userId=3이 먼저, userId=1이 나중에 (score 역순)
        queueRepository.addIfAbsent(3L, 3000.0);
        queueRepository.addIfAbsent(1L, 1000.0);
        queueRepository.addIfAbsent(2L, 2000.0);

        // when — 2명 꺼내기
        List<QueueEntry> entries = queueRepository.popMin(2);

        // then — score 오름차순: userId=1(1000), userId=2(2000)
        assertThat(entries).hasSize(2);
        assertThat(entries.get(0).userId()).isEqualTo(1L);
        assertThat(entries.get(1).userId()).isEqualTo(2L);

        // 대기열에 userId=3만 남음
        assertThat(queueRepository.getSize()).isEqualTo(1);
        assertThat(queueRepository.getRank(3L)).isEqualTo(0L);
    }

    @Test
    @DisplayName("빈 대기열에서 popMin은 빈 리스트를 반환한다")
    void popMin_EmptyQueue_ShouldReturnEmptyList() {
        List<QueueEntry> entries = queueRepository.popMin(14);
        assertThat(entries).isEmpty();
    }

    @Test
    @DisplayName("요청 수보다 대기 인원이 적으면 전부 꺼낸다")
    void popMin_LessThanCount_ShouldReturnAll() {
        // given
        queueRepository.addIfAbsent(1L, 1000.0);
        queueRepository.addIfAbsent(2L, 2000.0);

        // when — 14명 요청했지만 2명만 있음
        List<QueueEntry> entries = queueRepository.popMin(14);

        // then
        assertThat(entries).hasSize(2);
        assertThat(queueRepository.getSize()).isEqualTo(0);
    }

    // ============================
    // 동시성 테스트
    // ============================

    @Test
    @DisplayName("100명이 동시 진입해도 순번 0~99 범위를 유지한다")
    void concurrent_100Users_ShouldMaintainOrder() throws InterruptedException {
        // given
        int threadCount = 100;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(1);
        AtomicInteger addedCount = new AtomicInteger(0);

        // when — 100명 동시 ZADD
        for (int i = 0; i < threadCount; i++) {
            long userId = i + 1;
            executor.submit(() -> {
                try {
                    latch.await();
                    boolean added = queueRepository.addIfAbsent(userId, System.currentTimeMillis());
                    if (added) addedCount.incrementAndGet();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
        }
        latch.countDown();
        executor.shutdown();
        executor.awaitTermination(10, java.util.concurrent.TimeUnit.SECONDS);

        // then
        assertThat(addedCount.get()).isEqualTo(100);
        assertThat(queueRepository.getSize()).isEqualTo(100);

        // 모든 유저의 순번이 0~99 범위
        for (long userId = 1; userId <= 100; userId++) {
            Long rank = queueRepository.getRank(userId);
            assertThat(rank).isNotNull();
            assertThat(rank).isBetween(0L, 99L);
        }
    }
}
