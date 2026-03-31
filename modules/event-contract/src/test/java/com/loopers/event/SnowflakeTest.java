package com.loopers.event;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

class SnowflakeTest {

    Snowflake snowflake = new Snowflake();

    @Test
    @DisplayName("멀티스레드 환경에서 생성된 ID는 유니크하고 각 작업 내에서는 증가한다")
    void nextIdIsUniqueAndIncreasingWithinEachTaskUnderConcurrency() throws ExecutionException, InterruptedException {
        // Arrange
        ExecutorService executorService = Executors.newFixedThreadPool(10);
        try {
            List<Future<List<Long>>> futures = new ArrayList<>();
            int repeatCount = 1000;
            int idCount = 1000;

            // Act
            for (int i = 0; i < repeatCount; i++) {
                futures.add(executorService.submit(() -> generateIdList(snowflake, idCount)));
            }

            // Assert
            List<Long> result = new ArrayList<>();
            for (Future<List<Long>> future : futures) {
                List<Long> idList = future.get();
                for (int i = 1; i < idList.size(); i++) {
                    assertThat(idList.get(i)).isGreaterThan(idList.get(i - 1));
                }
                result.addAll(idList);
            }

            assertThat(result.stream().distinct().count()).isEqualTo((long) repeatCount * idCount);
        } finally {
            executorService.shutdown();
        }
    }

    private List<Long> generateIdList(Snowflake snowflake, int count) {
        List<Long> ids = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            ids.add(snowflake.nextId());
        }
        return ids;
    }
}
