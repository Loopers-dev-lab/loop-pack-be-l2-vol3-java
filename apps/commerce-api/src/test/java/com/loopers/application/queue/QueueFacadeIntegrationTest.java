package com.loopers.application.queue;

import com.loopers.testcontainers.MySqlTestContainersConfig;
import com.loopers.testcontainers.RedisTestContainersConfig;
import com.loopers.utils.DatabaseCleanUp;
import com.loopers.utils.RedisCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import({MySqlTestContainersConfig.class, RedisTestContainersConfig.class})
class QueueFacadeIntegrationTest {

    @Autowired
    private QueueFacade queueFacade;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @Autowired
    private RedisCleanUp redisCleanUp;

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
}

