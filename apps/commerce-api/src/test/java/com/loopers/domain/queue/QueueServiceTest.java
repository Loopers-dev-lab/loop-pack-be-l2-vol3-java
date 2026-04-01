package com.loopers.domain.queue;

import com.loopers.application.queue.EntryTokenScheduler;
import com.loopers.support.error.CoreException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class QueueServiceTest {

    @MockitoBean
    private EntryTokenScheduler entryTokenScheduler;

    @Autowired
    private QueueService queueService;

    @Autowired
    @Qualifier("redisTemplateMaster")
    private RedisTemplate<String, String> masterRedisTemplate;

    @BeforeEach
    void setUp() {
        // 테스트 전 대기열 초기화
        masterRedisTemplate.delete("queue:waiting");
    }

    @Nested
    @DisplayName("대기열 진입")
    class Enter {

        @DisplayName("대기열에 진입하면 순번이 반환된다")
        @Test
        void enterReturnsRank() {
            Long rank = queueService.enter(1L);
            assertThat(rank).isEqualTo(1);
        }

        @DisplayName("같은 유저가 중복 진입하면 예외가 발생한다")
        @Test
        void duplicateEnterThrows() {
            queueService.enter(1L);

            assertThatThrownBy(() -> queueService.enter(1L))
                    .isInstanceOf(CoreException.class);
        }

        @DisplayName("여러 명 진입 시 순번이 진입 순서대로 부여된다")
        @Test
        void multipleEnterInOrder() {
            queueService.enter(1L);
            queueService.enter(2L);
            queueService.enter(3L);

            assertThat(queueService.getRank(1L)).isEqualTo(1);
            assertThat(queueService.getRank(2L)).isEqualTo(2);
            assertThat(queueService.getRank(3L)).isEqualTo(3);
        }
    }

    @Nested
    @DisplayName("대기 인원 조회")
    class QueueSize {

        @DisplayName("전체 대기 인원이 정확하다")
        @Test
        void queueSizeIsAccurate() {
            queueService.enter(1L);
            queueService.enter(2L);
            queueService.enter(3L);

            assertThat(queueService.getQueueSize()).isEqualTo(3);
        }
    }

    @Nested
    @DisplayName("순번 조회")
    class GetRank {

        @DisplayName("대기열에 없는 유저는 null을 반환한다")
        @Test
        void nonExistentUserReturnsNull() {
            assertThat(queueService.getRank(999L)).isNull();
        }
    }

    @Nested
    @DisplayName("유저 꺼내기 (popUsers)")
    class PopUsers {

        @DisplayName("N명을 꺼내면 대기열에서 제거된다")
        @Test
        void popRemovesFromQueue() {
            queueService.enter(1L);
            queueService.enter(2L);
            queueService.enter(3L);

            queueService.popUsers(2);

            assertThat(queueService.getQueueSize()).isEqualTo(1);
            assertThat(queueService.getRank(3L)).isEqualTo(1);  // 3번 유저가 이제 1번
        }
    }
}
