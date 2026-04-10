package com.loopers.infrastructure.queue;

import com.loopers.application.queue.QueueConstants;
import com.loopers.application.queue.QueueEntryScheduler;
import com.loopers.domain.queue.QueueRepository;
import com.loopers.domain.queue.QueueTokenRepository;
import com.loopers.testcontainers.MySqlTestContainersConfig;
import com.loopers.testcontainers.RedisTestContainersConfig;
import com.loopers.utils.RedisCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import({MySqlTestContainersConfig.class, RedisTestContainersConfig.class})
@ActiveProfiles("test")
class QueueThroughputIntegrationTest {

    @Autowired
    private QueueRepository queueRepository;

    @Autowired
    private QueueTokenRepository queueTokenRepository;

    @Autowired
    private QueueEntryScheduler queueEntryScheduler;

    @Autowired
    private RedisCleanUp redisCleanUp;

    @AfterEach
    void tearDown() {
        redisCleanUp.truncateAll();
    }

    @Test
    @DisplayName("배치 크기보다 많은 유저가 대기해도 스케줄러는 배치 크기만큼만 처리한다")
    void 배치_크기_이상_요청_시_배치_크기만큼만_처리한다() {
        // given
        String eventId = "throughput-test";
        int totalUsers = QueueConstants.BATCH_SIZE * 3;
        for (int i = 1; i <= totalUsers; i++) {
            queueRepository.add(eventId, (long) i);
        }
        assertThat(queueRepository.getTotalCount(eventId)).isEqualTo(totalUsers);

        // when
        queueEntryScheduler.processQueue(eventId);

        // then
        assertThat(queueRepository.getTotalCount(eventId)).isEqualTo(totalUsers - QueueConstants.BATCH_SIZE);

        int tokenCount = 0;
        for (int i = 1; i <= totalUsers; i++) {
            if (queueTokenRepository.getToken(eventId, (long) i).isPresent()) {
                tokenCount++;
            }
        }
        assertThat(tokenCount).isEqualTo(QueueConstants.BATCH_SIZE);
    }

    @Test
    @DisplayName("스케줄러를 여러 번 실행하면 모든 대기자가 순차적으로 토큰을 받는다")
    void 스케줄러_반복_실행_시_모든_대기자가_토큰을_받는다() {
        // given
        String eventId = "throughput-test";
        int totalUsers = QueueConstants.BATCH_SIZE * 3;
        for (int i = 1; i <= totalUsers; i++) {
            queueRepository.add(eventId, (long) i);
        }

        // when
        queueEntryScheduler.processQueue(eventId);
        queueEntryScheduler.processQueue(eventId);
        queueEntryScheduler.processQueue(eventId);

        // then
        assertThat(queueRepository.getTotalCount(eventId)).isZero();

        int tokenCount = 0;
        for (int i = 1; i <= totalUsers; i++) {
            if (queueTokenRepository.getToken(eventId, (long) i).isPresent()) {
                tokenCount++;
            }
        }
        assertThat(tokenCount).isEqualTo(totalUsers);
    }

    @Test
    @DisplayName("대기열이 비어있을 때 스케줄러를 실행해도 에러가 발생하지 않는다")
    void 빈_대기열에서_스케줄러_실행해도_안전하다() {
        // when & then
        queueEntryScheduler.processQueue("throughput-test");
        assertThat(queueRepository.getTotalCount("throughput-test")).isZero();
    }
}
