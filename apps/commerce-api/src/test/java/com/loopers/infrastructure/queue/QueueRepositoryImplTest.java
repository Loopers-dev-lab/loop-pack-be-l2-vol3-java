package com.loopers.infrastructure.queue;

import com.loopers.domain.queue.QueueRepository;
import com.loopers.testcontainers.RedisTestContainersConfig;
import com.loopers.utils.RedisCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@Import(RedisTestContainersConfig.class)
@SpringBootTest
class QueueRepositoryImplTest {

    @Autowired
    private QueueRepository queueRepository;

    @Autowired
    private RedisCleanUp redisCleanUp;

    @AfterEach
    void tearDown() {
        redisCleanUp.truncateAll();
    }

    @DisplayName("enter 호출 시, ")
    @Nested
    class Enter {

        @DisplayName("userId 가 queue:waiting 에 추가되고 진입 순서대로 rank 가 부여된다.")
        @Test
        void addsUserToWaitingWithRank() {
            // arrange
            long firstUserId = 1L;
            long secondUserId = 2L;

            // act
            queueRepository.enter(firstUserId, 1000.0);
            queueRepository.enter(secondUserId, 2000.0);

            // assert
            assertThat(queueRepository.isInWaiting(firstUserId)).isTrue();
            assertThat(queueRepository.getRank(firstUserId)).isEqualTo(Optional.of(0L));
            assertThat(queueRepository.getRank(secondUserId)).isEqualTo(Optional.of(1L));
        }
    }

    @DisplayName("issueTokens 호출 시, ")
    @Nested
    class IssueTokens {

        @DisplayName("count 만큼 waiting 에서 꺼내고 각 유저에게 토큰이 발급된다.")
        @Test
        void movesUsersAndIssuesTokens() {
            // arrange
            queueRepository.enter(1L, 1000.0);
            queueRepository.enter(2L, 2000.0);
            queueRepository.enter(3L, 3000.0);
            List<String> uuids = List.of("uuid-1", "uuid-2");

            // act
            List<Long> moved = queueRepository.issueTokens(2, 180L, uuids);

            // assert
            assertThat(moved).containsExactly(1L, 2L);
            assertThat(queueRepository.isInWaiting(1L)).isFalse();
            assertThat(queueRepository.isInWaiting(2L)).isFalse();
            assertThat(queueRepository.isInWaiting(3L)).isTrue();
            assertThat(queueRepository.findToken(1L)).isEqualTo(Optional.of("uuid-1"));
            assertThat(queueRepository.findToken(2L)).isEqualTo(Optional.of("uuid-2"));
        }

        @DisplayName("대기 인원이 count 보다 많아도 한 번에 count 명만 발급되고 나머지는 waiting 에 남는다.")
        @Test
        void issuesOnlyBatchSize_whenWaitingExceedsBatchSize() {
            // arrange
            queueRepository.enter(1L, 1000.0);
            queueRepository.enter(2L, 2000.0);
            queueRepository.enter(3L, 3000.0);
            queueRepository.enter(4L, 4000.0);
            queueRepository.enter(5L, 5000.0);

            // act
            List<Long> issued = queueRepository.issueTokens(2, 180L, List.of("uuid-1", "uuid-2"));

            // assert
            assertThat(issued).containsExactly(1L, 2L);
            assertThat(queueRepository.findToken(1L)).hasValue("uuid-1");
            assertThat(queueRepository.findToken(2L)).hasValue("uuid-2");

            assertThat(queueRepository.isInWaiting(3L)).isTrue();
            assertThat(queueRepository.isInWaiting(4L)).isTrue();
            assertThat(queueRepository.isInWaiting(5L)).isTrue();
            assertThat(queueRepository.getRank(3L)).hasValue(0L);
            assertThat(queueRepository.getRank(4L)).hasValue(1L);
            assertThat(queueRepository.getRank(5L)).hasValue(2L);
        }

        @DisplayName("waiting 이 비어있으면 빈 리스트를 반환한다.")
        @Test
        void returnsEmptyList_whenWaitingIsEmpty() {
            // act
            List<Long> moved = queueRepository.issueTokens(5, 180L, List.of("uuid-1"));

            // assert
            assertThat(moved).isEmpty();
        }

        @DisplayName("짧은 TTL 로 발급한 토큰은 만료 후 조회되지 않는다.")
        @Test
        void expiresTokenAfterTtl() throws InterruptedException {
            // arrange
            queueRepository.enter(1L, 1000.0);

            // act
            queueRepository.issueTokens(1, 1L, List.of("uuid-1"));
            waitUntilTokenExpires(1L);

            // assert
            assertThat(queueRepository.findToken(1L)).isEmpty();
        }
    }

    @DisplayName("findToken 호출 시, ")
    @Nested
    class FindToken {

        @DisplayName("토큰이 없으면 empty 를 반환한다.")
        @Test
        void returnsEmpty_whenNoToken() {
            // act & assert
            assertThat(queueRepository.findToken(999L)).isEmpty();
        }
    }

    @DisplayName("removeToken 호출 시, ")
    @Nested
    class RemoveToken {

        @DisplayName("token 키가 삭제된다.")
        @Test
        void deletesTokenKey() {
            // arrange
            queueRepository.enter(1L, 1000.0);
            queueRepository.issueTokens(1, 180L, List.of("uuid-1"));

            // act
            queueRepository.removeToken(1L);

            // assert
            assertThat(queueRepository.findToken(1L)).isEmpty();
        }
    }

    private void waitUntilTokenExpires(long userId) throws InterruptedException {
        long timeoutAt = System.currentTimeMillis() + 3_000L;
        while (System.currentTimeMillis() < timeoutAt) {
            if (queueRepository.findToken(userId).isEmpty()) {
                return;
            }
            Thread.sleep(100L);
        }
    }
}
