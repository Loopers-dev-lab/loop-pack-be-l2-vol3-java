package com.loopers.application.queue;

import com.loopers.domain.queue.EntryTokenService;
import com.loopers.domain.queue.WaitingQueueService;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import com.loopers.utils.RedisCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@TestPropertySource(properties = {"queue.enabled=true", "queue.token-ttl-seconds=1"})
@DisplayName("토큰 TTL 만료 통합 테스트")
class EntryTokenTtlTest {

    @Autowired
    private QueueApp queueApp;

    @Autowired
    private WaitingQueueService waitingQueueService;

    @Autowired
    private EntryTokenService entryTokenService;

    @Autowired
    private RedisCleanUp redisCleanUp;

    @AfterEach
    void tearDown() {
        redisCleanUp.truncateAll();
    }

    @Test
    @DisplayName("시나리오 2: 토큰 TTL 만료 후 대기열에도 없고 토큰도 없는 상태 → NOT_IN_QUEUE")
    void tokenTtlExpiry_notInQueueAfterExpiry() throws InterruptedException {
        // given — 진입 후 스케줄러로 토큰 발급
        Long memberId = 1L;
        queueApp.enterQueue(memberId);
        waitingQueueService.popN(1);
        entryTokenService.issue(memberId);

        // 토큰이 존재하는지 확인
        assertThat(entryTokenService.findToken(memberId)).isPresent();

        // when — TTL(1초) 만료 대기
        Thread.sleep(1500);

        // then — 토큰 만료 + 대기열에서도 제거됨 → QUEUE_NOT_FOUND
        assertThat(entryTokenService.findToken(memberId)).isEmpty();
        assertThatThrownBy(() -> queueApp.getQueueStatus(memberId))
                .isInstanceOf(CoreException.class)
                .extracting("errorType")
                .isEqualTo(ErrorType.QUEUE_NOT_FOUND);
    }
}
