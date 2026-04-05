package com.loopers.infrastructure.queue;

import com.loopers.application.queue.QueueService;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = "queue.scheduler.enabled=false")
class RedisQueueServiceTest {

    @Autowired
    private QueueService queueService;

    @Autowired
    private StringRedisTemplate redisTemplate;


    private static final String QUEUE_KEY = "order:waiting-queue";
    private static final String QUEUE_SEQUENCE_KEY = "order:waiting-queue:sequence";

    @AfterEach
    void tearDown() {
        redisTemplate.delete(QUEUE_KEY);
        redisTemplate.delete(QUEUE_SEQUENCE_KEY);
    }

    @Test
    void 처음_진입하면_0번_rank를_반환한다() {
        long rank = queueService.enter(1L);

        assertThat(rank).isEqualTo(0L);
    }

    @Test
    void 이미_대기_중인_유저가_다시_진입해도_기존_rank를_반환한다() {
        queueService.enter(1L); // 0번
        queueService.enter(2L); // 1번

        long rank = queueService.enter(1L); // NX라서 여전히 0번

        assertThat(rank).isEqualTo(0L);
    }

    @Test
    void 먼저_진입한_유저가_더_낮은_rank를_갖는다() {
        long rank1 = queueService.enter(1L);
        long rank2 = queueService.enter(2L);
        long rank3 = queueService.enter(3L);

        assertThat(rank1).isLessThan(rank2);
        assertThat(rank2).isLessThan(rank3);
    }

    @Test
    void 대기_중인_유저의_rank를_조회할_수_있다() {
        queueService.enter(1L);
        queueService.enter(2L);

        long rank = queueService.getRank(2L);

        assertThat(rank).isEqualTo(1L);
    }

    @Test
    void 대기열에_없는_유저의_rank를_조회하면_예외가_발생한다() {
        assertThatThrownBy(() -> queueService.getRank(999L))
                .isInstanceOf(CoreException.class);
    }

    @Test
    void 전체_대기_인원을_조회할_수_있다() {
        queueService.enter(1L);
        queueService.enter(2L);
        queueService.enter(3L);

        long size = queueService.getSize();

        assertThat(size).isEqualTo(3L);
    }

}
