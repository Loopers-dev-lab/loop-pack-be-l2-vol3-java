package com.loopers.application.queue;

import com.loopers.domain.queue.EntryTokenRepository;
import com.loopers.domain.queue.QueuePosition;
import com.loopers.domain.queue.WaitingQueueRepository;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
public class EntryTokenSchedulerIntegrationTest {

    private static final String QUEUE_KEY = "waiting-queue:order";

    @Autowired
    private EntryTokenScheduler entryTokenScheduler;

    @Autowired
    private WaitingQueueRepository waitingQueueRepository;

    @Autowired
    private EntryTokenRepository entryTokenRepository;

    @Autowired
    private QueueFacade queueFacade;

    @Autowired
    private RedisTemplate<String, String> redisTemplateMaster;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        redisTemplateMaster.delete(QUEUE_KEY);
        for (long i = 1; i <= 30; i++) {
            entryTokenRepository.delete(i);
        }
        databaseCleanUp.truncateAllTables();
    }

    @Nested
    @DisplayName("스케줄러 토큰 발급")
    class IssueTokens {

        @Test
        @DisplayName("큐에 20명이 있을 때 스케줄러 1회 실행 시 14명에게 토큰을 발급한다")
        void 스케줄러_실행_시_N명_토큰_발급() {
            // arrange — 20명 진입 (userId 1~20)
            for (long i = 1; i <= 20; i++) {
                waitingQueueRepository.enqueue(i, System.currentTimeMillis() + i);
            }

            // act
            entryTokenScheduler.issueTokens();

            // assert — 상위 14명(1~14)에게 토큰 발급됨 (batch-size=14)
            // 백그라운드 스케줄러가 즉시 ZREM된 자리를 채울 수 있으므로 전체 카운트 대신
            // 반드시 발급돼야 하는 첫 배치(1~14) 만 확인
            for (long i = 1; i <= 14; i++) {
                assertThat(entryTokenRepository.existsByUserId(i)).isTrue();
            }
        }

        @Test
        @DisplayName("스케줄러 실행 후 토큰이 발급된 14명이 큐에서 즉시 제거된다")
        void 스케줄러_실행_후_토큰_발급된_유저_큐에서_제거() {
            // arrange
            for (long i = 1; i <= 20; i++) {
                waitingQueueRepository.enqueue(i, System.currentTimeMillis() + i);
            }

            // act
            entryTokenScheduler.issueTokens();

            // assert — 상위 14명 토큰 발급 + 즉시 ZREM → 6명 잔여
            assertThat(waitingQueueRepository.getTotalCount()).isEqualTo(6);
        }

        @Test
        @DisplayName("빈 큐에서 스케줄러를 실행해도 예외 없이 정상 종료된다")
        void 빈_큐에서_스케줄러_실행() {
            // act & assert — 예외 없이 정상 종료
            entryTokenScheduler.issueTokens();
        }

        @Test
        @DisplayName("이미 토큰이 있는 유저는 스케줄러 재실행 시 토큰이 덮어써지지 않는다")
        void 스케줄러_멱등성() throws InterruptedException {
            // arrange — userId=1 진입 후 토큰 발급
            waitingQueueRepository.enqueue(1L, System.currentTimeMillis());
            entryTokenScheduler.issueTokens();

            // 기존 토큰 값 기록
            String existingTokenKey = "entry-token:1";
            String originalToken = redisTemplateMaster.opsForValue().get(existingTokenKey);
            Long originalTtl = redisTemplateMaster.getExpire(existingTokenKey);

            Thread.sleep(100); // 시간 경과 후 재실행

            // act — 스케줄러 재실행
            entryTokenScheduler.issueTokens();

            // assert — SET NX로 인해 토큰 값과 TTL이 변하지 않음
            String currentToken = redisTemplateMaster.opsForValue().get(existingTokenKey);
            Long currentTtl = redisTemplateMaster.getExpire(existingTokenKey);

            assertThat(currentToken).isEqualTo(originalToken);
            assertThat(currentTtl).isLessThanOrEqualTo(originalTtl); // TTL이 리셋되지 않고 줄어드는 중
        }
    }

    @Nested
    @DisplayName("폴링 시 토큰 감지 및 큐 제거")
    class PollingDetection {

        @Test
        @DisplayName("스케줄러가 토큰 발급 즉시 ZREM하므로 폴링 시 rank가 null이어도 tokenIssued=true를 반환한다")
        void 폴링_시_토큰_있으면_tokenIssued_반환() {
            // arrange — 진입 후 스케줄러로 토큰 발급 + 즉시 ZREM
            waitingQueueRepository.enqueue(1L, System.currentTimeMillis());
            entryTokenScheduler.issueTokens();

            // 스케줄러가 토큰 발급과 동시에 ZREM → 큐에 없음
            assertThat(waitingQueueRepository.getTotalCount()).isEqualTo(0);
            assertThat(entryTokenRepository.existsByUserId(1L)).isTrue();

            // act — 폴링 API 호출 (rank==null + token 존재 경로)
            QueuePosition position = queueFacade.getPosition(1L);

            // assert
            assertThat(position.tokenIssued()).isTrue();
        }

        @Test
        @DisplayName("토큰 TTL 만료 후 existsByUserId는 false를 반환한다")
        void 토큰_TTL_만료() throws InterruptedException {
            // arrange — TTL 1초로 직접 발급
            entryTokenRepository.issueIfAbsent(1L, "test-token", 1L);
            assertThat(entryTokenRepository.existsByUserId(1L)).isTrue();

            // act — TTL 만료 대기
            Thread.sleep(1500);

            // assert
            assertThat(entryTokenRepository.existsByUserId(1L)).isFalse();
        }
    }
}
