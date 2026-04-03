package com.loopers.application.queue;

import com.loopers.application.queue.EntryScheduler;
import com.loopers.testcontainers.MySqlTestContainersConfig;
import com.loopers.testcontainers.RedisTestContainersConfig;
import com.loopers.utils.DatabaseCleanUp;
import com.loopers.utils.RedisCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;

/** 스케줄러가 테스트 중 큐 방출해 테스트 간섭이 발생. 이를 방지하기 위한 스케줄러를 비활성화. */
@SpringBootTest(
    properties = {
        "spring.task.scheduling.enabled=false",
        "queue.fallback.enabled=false"
    }
)
@Import({MySqlTestContainersConfig.class, RedisTestContainersConfig.class})
class QueueFacadeIntegrationTest {

    @MockBean
    @SuppressWarnings("unused")
    private EntryScheduler entryScheduler;

    @Autowired
    private QueueFacade queueFacade;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @Autowired
    private RedisCleanUp redisCleanUp;

    @BeforeEach
    void setUp() {
        databaseCleanUp.truncateAllTables();
        redisCleanUp.truncateAll();
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
        redisCleanUp.truncateAll();
    }

    @DisplayName("joinQueue_withFirstUser_shouldReturnZeroPosition")
    @Test
    void joinQueue_withFirstUser_shouldReturnZeroPosition() {
        // given
        Long userId = 1L;

        // when
        QueueInfo result = queueFacade.joinQueue(userId);

        // then
        assertThat(result.position()).isEqualTo(0L);
        assertThat(result.totalWaiting()).isEqualTo(1L);
    }

    @DisplayName("joinQueue_withDuplicateUser_shouldKeepSamePosition")
    @Test
    void joinQueue_withDuplicateUser_shouldKeepSamePosition() {
        // given
        Long userId = 1L;
        QueueInfo first = queueFacade.joinQueue(userId);

        // when
        QueueInfo second = queueFacade.joinQueue(userId);

        // then
        assertThat(second.position()).isEqualTo(first.position());
        assertThat(second.totalWaiting()).isEqualTo(first.totalWaiting());
    }

    @DisplayName("join 후 getQueuePosition 하면 순번·Retry-After 대응 힌트가 채워진다.")
    @Test
    void getQueuePosition_afterJoin_shouldReturnPositionAndHints() {
        Long userId = 1L;
        queueFacade.joinQueue(userId);

        var opt = queueFacade.getQueuePosition(userId);

        assertThat(opt).isPresent();
        assertThat(opt.get().position()).isEqualTo(0L);
        assertThat(opt.get().totalWaiting()).isEqualTo(1L);
        assertThat(opt.get().suggestedPollIntervalMs()).isEqualTo(1000L);
        assertThat(opt.get().retryAfterSeconds()).isEqualTo(1L);
    }

    @DisplayName("진입 없이 getQueuePosition 하면 empty")
    @Test
    void getQueuePosition_withoutJoin_shouldReturnEmpty() {
        assertThat(queueFacade.getQueuePosition(999L)).isEmpty();
    }
}

