package com.loopers.infrastructure.queue;

import com.loopers.application.queue.OrderQueueReader;
import com.loopers.application.queue.OrderQueueWriter;
import com.loopers.utils.RedisCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class OrderQueueFlagIntegrationTest {

    @Autowired
    private OrderQueueWriter writer;

    @Autowired
    private OrderQueueReader reader;

    @Autowired
    private RedisCleanUp redisCleanUp;

    @AfterEach
    void tearDown() {
        writer.setEnabled(false);
        redisCleanUp.truncateAll();
    }

    private void awaitEnabled(boolean expected) throws InterruptedException {
        for (int i = 0; i < 50; i++) {
            if (reader.isEnabled() == expected) return;
            Thread.sleep(10);
        }
    }

    @DisplayName("대기열 피처 플래그 Pub/Sub 캐시 동기화")
    @Nested
    class PubSubSync {

        @DisplayName("Writer로 활성화하면 Reader 로컬 캐시에 반영된다.")
        @Test
        void readerReflectsEnabled_afterWriterSetTrue() throws InterruptedException {
            // act
            writer.setEnabled(true);
            awaitEnabled(true);

            // assert
            assertThat(reader.isEnabled()).isTrue();
        }

        @DisplayName("Writer로 비활성화하면 Reader 로컬 캐시에 반영된다.")
        @Test
        void readerReflectsDisabled_afterWriterSetFalse() throws InterruptedException {
            // arrange
            writer.setEnabled(true);
            awaitEnabled(true);

            // act
            writer.setEnabled(false);
            awaitEnabled(false);

            // assert
            assertThat(reader.isEnabled()).isFalse();
        }
    }

    @DisplayName("Bootstrap 기본값")
    @Nested
    class Bootstrap {

        @DisplayName("Redis에 키가 없으면 기본값은 false이다.")
        @Test
        void defaultsToFalse_whenNoKeyInRedis() {
            // assert: 앱 기동 후 Redis에 키 없는 상태
            assertThat(reader.isEnabled()).isFalse();
        }
    }
}
