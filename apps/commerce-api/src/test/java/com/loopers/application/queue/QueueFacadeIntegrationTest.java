package com.loopers.application.queue;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import com.loopers.domain.queue.QueuePosition;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.core.RedisTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
public class QueueFacadeIntegrationTest {

    private static final Long USER_ID = 1L;
    private static final Long OTHER_USER_ID = 2L;
    private static final String QUEUE_KEY = "waiting-queue:order";

    // 이 테스트는 대기열 진입/순번 로직만 검증한다.
    // @Scheduled 스케줄러가 100ms마다 자동 실행되면 토큰이 발급되어 테스트가 불안정해지므로
    // MockBean으로 스케줄러를 비활성화한다.
    @MockBean
    private EntryTokenScheduler entryTokenScheduler;

    @Autowired
    private QueueFacade queueFacade;

    @Autowired
    private RedisTemplate<String, String> redisTemplateMaster;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        redisTemplateMaster.delete(QUEUE_KEY);
        // 테스트에서 사용하는 userId 범위의 토큰 정리 (다른 테스트 클래스의 스케줄러가 발급한 토큰 포함)
        for (long i = 1; i <= 36; i++) {
            redisTemplateMaster.delete("entry-token:" + i);
        }
        databaseCleanUp.truncateAllTables();
    }

    @Nested
    @DisplayName("대기열 진입")
    class EnterQueue {

        @Test
        @DisplayName("신규 진입 시 대기열에 추가되고 순번을 반환한다")
        void 대기열_진입_성공() {
            // act
            QueuePosition position = queueFacade.enterQueue(USER_ID);

            // assert
            assertThat(position.position()).isEqualTo(1);
            assertThat(position.totalWaiting()).isEqualTo(1);
            assertThat(position.tokenIssued()).isFalse(); // 아직 토큰 미발급 상태
        }

        @Test
        @DisplayName("이미 대기 중인 유저가 재진입 시 맨 뒤로 이동한다")
        void 재진입_시_맨_뒤로_이동() {
            // arrange — USER_ID가 먼저 진입하고, OTHER_USER_ID가 뒤에 진입
            queueFacade.enterQueue(USER_ID);
            queueFacade.enterQueue(OTHER_USER_ID);

            // act — USER_ID가 다시 진입 (재진입)
            QueuePosition position = queueFacade.enterQueue(USER_ID);

            // assert — score가 갱신되어 OTHER_USER_ID보다 뒤로 이동
            assertThat(position.position()).isEqualTo(2);
            assertThat(position.totalWaiting()).isEqualTo(2); // 인원 증가 없음
        }

        @Test
        @DisplayName("여러 유저 진입 시 진입 순서대로 순번이 부여된다")
        void 여러_유저_진입_순번_순서() {
            // arrange
            queueFacade.enterQueue(USER_ID);

            // act
            QueuePosition position = queueFacade.enterQueue(OTHER_USER_ID);

            // assert
            assertThat(position.position()).isEqualTo(2);
            assertThat(position.totalWaiting()).isEqualTo(2);
        }
    }

    @Nested
    @DisplayName("순번 조회")
    class GetPosition {

        @Test
        @DisplayName("대기열에 없는 유저 조회 시 NOT_FOUND 예외가 발생한다")
        void 순번_조회_대기열에_없는_유저() {
            // act & assert
            CoreException exception = assertThrows(CoreException.class,
                    () -> queueFacade.getPosition(USER_ID));
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }

        @Test
        @DisplayName("N명 진입 후 totalWaiting이 N이다")
        void 전체_대기_인원_조회() {
            // arrange
            queueFacade.enterQueue(USER_ID);
            queueFacade.enterQueue(OTHER_USER_ID);

            // act
            QueuePosition position = queueFacade.getPosition(USER_ID);

            // assert
            assertThat(position.totalWaiting()).isEqualTo(2);
        }
    }

    @Nested
    @DisplayName("예상 대기 시간 계산")
    class EstimatedWait {

        @Test
        @DisplayName("순번 36, 배치 14, 간격 100ms이면 예상 대기 시간은 0초이다")
        void 예상_대기_시간_계산() {
            // arrange — 36명 진입, 내가 36번째
            for (long i = 1; i <= 36; i++) {
                queueFacade.enterQueue(i);
            }

            // act
            QueuePosition position = queueFacade.getPosition(36L);

            // assert — ceil(36 / 14) * 100ms / 1000 = 300ms / 1000 = 0초 (정수 나눗셈)
            assertThat(position.position()).isEqualTo(36);
            assertThat(position.estimatedWaitSeconds()).isEqualTo(0);
        }

        @Test
        @DisplayName("순번이 100 이하면 권장 폴링 주기가 1000ms이다")
        void 순번_100이내_폴링주기_1초() {
            // arrange
            queueFacade.enterQueue(USER_ID);

            // act
            QueuePosition position = queueFacade.getPosition(USER_ID);

            // assert
            assertThat(position.recommendedPollingIntervalMs()).isEqualTo(1000);
        }
    }
}
