package com.loopers.interfaces.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.loopers.domain.queue.EntryTokenRepository;
import com.loopers.domain.queue.WaitingQueueRepository;
import com.loopers.utils.RedisCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DisplayName("대기열 처리량 초과")
class QueueThroughputE2ETest {

    @Autowired
    private WaitingQueueRepository waitingQueueRepository;

    @Autowired
    private EntryTokenRepository entryTokenRepository;

    @Autowired
    private RedisCleanUp redisCleanUp;

    @AfterEach
    void tearDown() {
        redisCleanUp.truncateAll();
    }

    @Test
    @DisplayName("배치 크기를 초과하는 인원이 진입해도, 스케줄러가 순차적으로 전원에게 토큰을 발급한다")
    void scheduler_issues_tokens_for_all_users_over_multiple_batches() throws InterruptedException {
        // BATCH_SIZE = 20, 50명 진입
        int userCount = 50;
        for (long userId = 1001L; userId <= 1000L + userCount; userId++) {
            waitingQueueRepository.enter(userId);
        }

        // 스케줄러 100ms × 3배치 + 여유 = 500ms 대기
        Thread.sleep(500);

        long issuedCount = 0;
        for (long userId = 1001L; userId <= 1000L + userCount; userId++) {
            if (entryTokenRepository.findByUserId(userId).isPresent()) {
                issuedCount++;
            }
        }
        assertThat(issuedCount).isEqualTo(userCount);
    }
}
