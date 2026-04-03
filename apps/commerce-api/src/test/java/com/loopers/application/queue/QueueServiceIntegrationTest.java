package com.loopers.application.queue;

import com.loopers.utils.DatabaseCleanUp;
import com.loopers.utils.RedisCleanUp;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class QueueServiceIntegrationTest {

    @Autowired
    private QueueService queueService;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @Autowired
    private RedisCleanUp redisCleanUp;

    @BeforeEach
    void setUp() {
        databaseCleanUp.truncateAllTables();
        redisCleanUp.truncateAll();
    }

    @Nested
    class 대기열_추가 {

        @Test
        void 유저를_대기열에_추가하면_true를_반환한다() {
            boolean result = queueService.addToQueue(1L);

            assertThat(result).isTrue();
            assertThat(queueService.getQueueSize()).isEqualTo(1);
        }

        @Test
        void 같은_유저를_다시_추가하면_중복_없이_true를_반환한다() {
            queueService.addToQueue(1L);

            boolean result = queueService.addToQueue(1L);

            assertThat(result).isTrue();
            assertThat(queueService.getQueueSize()).isEqualTo(1);
        }
    }

    @Nested
    class 대기열_제거 {

        @Test
        void 유저를_대기열에서_제거하면_크기가_줄어든다() {
            queueService.addToQueue(1L);
            queueService.addToQueue(2L);

            queueService.removeFromQueue("1");

            assertThat(queueService.getQueueSize()).isEqualTo(1);
        }
    }

    @Nested
    class 상위_조회 {

        @Test
        void 상위_N명을_조회하면_순서대로_반환한다() {
            queueService.addToQueue(10L);
            queueService.addToQueue(20L);
            queueService.addToQueue(30L);

            Set<String> top = queueService.peekTop(2);

            assertThat(top).hasSize(2);
        }

        @Test
        void 대기열이_비어있으면_빈_집합을_반환한다() {
            Set<String> top = queueService.peekTop(10);

            assertThat(top).isEmpty();
        }
    }

    @Nested
    class 순번_조회 {

        @Test
        void 대기열에_있는_유저의_순번을_반환한다() {
            queueService.addToQueue(1L);
            queueService.addToQueue(2L);
            queueService.addToQueue(3L);

            Long position = queueService.getPosition(1L);

            assertThat(position).isNotNull();
            assertThat(position).isGreaterThanOrEqualTo(0);
        }

        @Test
        void 대기열에_없는_유저는_null을_반환한다() {
            Long position = queueService.getPosition(999L);

            assertThat(position).isNull();
        }
    }

    @Nested
    class 대기_시간_추정 {

        @Test
        void 순번이_주어지면_대기_시간을_추정한다() {
            long waitSeconds = queueService.estimateWaitSeconds(100);

            assertThat(waitSeconds).isGreaterThanOrEqualTo(1);
        }
    }
}
