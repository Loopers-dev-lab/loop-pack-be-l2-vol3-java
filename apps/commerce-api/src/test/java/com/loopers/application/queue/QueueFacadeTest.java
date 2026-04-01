package com.loopers.application.queue;

import com.loopers.domain.queue.EntryTokenService;
import com.loopers.domain.queue.QueueService;
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

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class QueueFacadeTest {

    @MockitoBean
    private EntryTokenScheduler entryTokenScheduler;

    @Autowired
    private QueueFacade queueFacade;

    @Autowired
    private QueueService queueService;

    @Autowired
    private EntryTokenService entryTokenService;

    @Autowired
    @Qualifier("redisTemplateMaster")
    private RedisTemplate<String, String> masterRedisTemplate;

    @BeforeEach
    void setUp() {
        masterRedisTemplate.delete("queue:waiting");
        Set<String> tokenKeys = masterRedisTemplate.keys("entry-token:*");
        if (tokenKeys != null && !tokenKeys.isEmpty()) {
            masterRedisTemplate.delete(tokenKeys);
        }
    }

    @Nested
    @DisplayName("순번 조회 3-상태 분기")
    class GetPosition {

        @DisplayName("대기열에 있는 유저는 waiting 상태와 예상 대기 시간을 반환한다")
        @Test
        void waitingState() {
            // given — 42번째 유저 생성
            for (int i = 1; i <= 42; i++) {
                queueService.enter((long) i);
            }

            // when
            QueuePositionInfo info = queueFacade.getPosition(42L);

            // then
            assertThat(info.rank()).isEqualTo(42);
            assertThat(info.token()).isNull();
            assertThat(info.estimatedWaitSeconds()).isEqualTo(1); // ceil(42 / 140) = 1
        }

        @DisplayName("예상 대기 시간은 올림 처리된다 (rank=1이면 0초가 아닌 1초)")
        @Test
        void estimatedWaitSecondsCeiling() {
            // given
            queueService.enter(1L);

            // when
            QueuePositionInfo info = queueFacade.getPosition(1L);

            // then — ceil(1 / 140) = 1, 정수 나눗셈이었다면 0
            assertThat(info.estimatedWaitSeconds()).isEqualTo(1);
        }

        @DisplayName("토큰이 발급된 유저는 ready 상태와 토큰을 반환한다")
        @Test
        void readyState() {
            // given — 대기열 진입 후 스케줄러가 꺼내서 토큰 발급
            queueService.enter(1L);
            queueService.popUsers(1);
            entryTokenService.issueToken(1L);

            // when
            QueuePositionInfo info = queueFacade.getPosition(1L);

            // then
            assertThat(info.rank()).isZero();
            assertThat(info.token()).isNotNull();
            assertThat(info.estimatedWaitSeconds()).isZero();
        }

        @DisplayName("대기열에도 없고 토큰도 없는 유저는 NOT_FOUND 예외가 발생한다")
        @Test
        void notFoundState() {
            assertThatThrownBy(() -> queueFacade.getPosition(999L))
                    .isInstanceOf(CoreException.class);
        }
    }
}
