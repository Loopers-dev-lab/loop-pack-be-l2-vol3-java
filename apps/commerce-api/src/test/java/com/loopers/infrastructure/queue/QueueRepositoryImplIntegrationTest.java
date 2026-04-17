package com.loopers.infrastructure.queue;

import com.loopers.domain.queue.QueueRepository;
import com.loopers.testcontainers.MySqlTestContainersConfig;
import com.loopers.testcontainers.RedisTestContainersConfig;
import com.loopers.utils.RedisCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import({MySqlTestContainersConfig.class, RedisTestContainersConfig.class})
@ActiveProfiles("test")
class QueueRepositoryImplIntegrationTest {

    @Autowired
    private QueueRepository queueRepository;

    @Autowired
    private RedisCleanUp redisCleanUp;

    @AfterEach
    void tearDown() {
        redisCleanUp.truncateAll();
    }

    @Nested
    @DisplayName("add")
    class Add {

        @Test
        @DisplayName("유저를 대기열에 추가하면 true를 반환한다")
        void 유저를_대기열에_추가하면_true를_반환한다() {
            // given
            String eventId = "event-1";
            Long userId = 1L;

            // when
            boolean result = queueRepository.add(eventId, userId);

            // then
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("이미 등록된 유저를 추가하면 false를 반환한다")
        void 이미_등록된_유저를_추가하면_false를_반환한다() {
            // given
            String eventId = "event-1";
            Long userId = 1L;
            queueRepository.add(eventId, userId);

            // when
            boolean result = queueRepository.add(eventId, userId);

            // then
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("다른 이벤트의 대기열은 독립적으로 관리된다")
        void 다른_이벤트의_대기열은_독립적으로_관리된다() {
            // given
            Long userId = 1L;
            queueRepository.add("event-1", userId);

            // when
            boolean result = queueRepository.add("event-2", userId);

            // then
            assertThat(result).isTrue();
        }
    }

    @Nested
    @DisplayName("getPosition")
    class GetPosition {

        @Test
        @DisplayName("대기열에 있는 유저의 순위를 반환한다")
        void 대기열에_있는_유저의_순위를_반환한다() {
            // given
            String eventId = "event-1";
            queueRepository.add(eventId, 1L);
            queueRepository.add(eventId, 2L);
            queueRepository.add(eventId, 3L);

            // when
            Optional<Long> position = queueRepository.getPosition(eventId, 2L);

            // then
            assertThat(position).isPresent();
            assertThat(position.get()).isEqualTo(1L);
        }

        @Test
        @DisplayName("대기열에 없는 유저는 empty를 반환한다")
        void 대기열에_없는_유저는_empty를_반환한다() {
            // given
            String eventId = "event-1";
            queueRepository.add(eventId, 1L);

            // when
            Optional<Long> position = queueRepository.getPosition(eventId, 999L);

            // then
            assertThat(position).isEmpty();
        }
    }

    @Nested
    @DisplayName("getTotalCount")
    class GetTotalCount {

        @Test
        @DisplayName("대기열의 총 인원수를 반환한다")
        void 대기열의_총_인원수를_반환한다() {
            // given
            String eventId = "event-1";
            queueRepository.add(eventId, 1L);
            queueRepository.add(eventId, 2L);
            queueRepository.add(eventId, 3L);

            // when
            long count = queueRepository.getTotalCount(eventId);

            // then
            assertThat(count).isEqualTo(3);
        }

        @Test
        @DisplayName("빈 대기열은 0을 반환한다")
        void 빈_대기열은_0을_반환한다() {
            // when
            long count = queueRepository.getTotalCount("empty-event");

            // then
            assertThat(count).isZero();
        }
    }

    @Nested
    @DisplayName("popFront")
    class PopFront {

        @Test
        @DisplayName("앞에서 N명을 꺼내고 대기열에서 제거한다")
        void 앞에서_N명을_꺼내고_대기열에서_제거한다() {
            // given
            String eventId = "event-1";
            queueRepository.add(eventId, 1L);
            queueRepository.add(eventId, 2L);
            queueRepository.add(eventId, 3L);

            // when
            List<Long> popped = queueRepository.popFront(eventId, 2);

            // then
            assertThat(popped).containsExactly(1L, 2L);
            assertThat(queueRepository.getTotalCount(eventId)).isEqualTo(1);
        }

        @Test
        @DisplayName("대기열보다 많이 요청하면 있는 만큼만 반환한다")
        void 대기열보다_많이_요청하면_있는_만큼만_반환한다() {
            // given
            String eventId = "event-1";
            queueRepository.add(eventId, 1L);
            queueRepository.add(eventId, 2L);

            // when
            List<Long> popped = queueRepository.popFront(eventId, 5);

            // then
            assertThat(popped).containsExactly(1L, 2L);
            assertThat(queueRepository.getTotalCount(eventId)).isZero();
        }
    }
}
