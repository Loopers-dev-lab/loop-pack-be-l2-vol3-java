package com.loopers.domain.queue;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;

class QueueDomainServiceTest {

    private QueueDomainService queueDomainService;
    private FakeWaitingQueueRepository waitingQueueRepository;
    private FakeEntryTokenRepository entryTokenRepository;

    private static final int TPS = 175;
    private static final int MAX_QUEUE_SIZE = 52500;

    @BeforeEach
    void setUp() {
        waitingQueueRepository = new FakeWaitingQueueRepository();
        entryTokenRepository = new FakeEntryTokenRepository();
        queueDomainService = new QueueDomainService(waitingQueueRepository, entryTokenRepository);
    }

    @DisplayName("대기열에 진입할 때, ")
    @Nested
    class Enter {

        @DisplayName("정상적으로 진입하면, 순번 정보를 반환한다.")
        @Test
        void returnsPosition_whenEnterSuccessfully() {
            QueuePosition position = queueDomainService.enter(1L, MAX_QUEUE_SIZE, TPS);

            assertAll(
                () -> assertThat(position.position()).isEqualTo(1),
                () -> assertThat(position.estimatedWaitSeconds()).isZero(),
                () -> assertThat(position.retryAfter()).isEqualTo(1)
            );
        }

        @DisplayName("중복 진입하면, 기존 순번을 반환한다.")
        @Test
        void returnsExistingPosition_whenAlreadyInQueue() {
            queueDomainService.enter(1L, MAX_QUEUE_SIZE, TPS);
            QueuePosition position = queueDomainService.enter(1L, MAX_QUEUE_SIZE, TPS);

            assertThat(position.position()).isEqualTo(1);
        }

        @DisplayName("대기열이 가득 찼으면, SERVICE_UNAVAILABLE 예외가 발생한다.")
        @Test
        void throwsServiceUnavailable_whenQueueIsFull() {
            int smallMax = 2;
            queueDomainService.enter(1L, smallMax, TPS);
            queueDomainService.enter(2L, smallMax, TPS);

            CoreException result = assertThrows(CoreException.class,
                () -> queueDomainService.enter(3L, smallMax, TPS));
            assertThat(result.getErrorType()).isEqualTo(ErrorType.SERVICE_UNAVAILABLE);
            assertThat(result.getMessage()).isEqualTo("대기열이 가득 찼습니다.");
        }
    }

    @DisplayName("Polling 결과를 조회할 때, ")
    @Nested
    class GetPollingResult {

        @DisplayName("대기열에 있으면, WAITING 상태와 순번을 반환한다.")
        @Test
        void returnsWaiting_whenInQueue() {
            queueDomainService.enter(1L, MAX_QUEUE_SIZE, TPS);

            QueuePollingResult result = queueDomainService.getPollingResult(1L, TPS);

            assertAll(
                () -> assertThat(result.status()).isEqualTo(QueueStatus.WAITING),
                () -> assertThat(result.position()).isNotNull(),
                () -> assertThat(result.position().position()).isEqualTo(1),
                () -> assertThat(result.token()).isNull()
            );
        }

        @DisplayName("토큰이 발급되었으면, TOKEN_ISSUED 상태와 토큰을 반환한다.")
        @Test
        void returnsTokenIssued_whenTokenExists() {
            EntryToken token = new EntryToken(1L, "test-token", System.currentTimeMillis());
            entryTokenRepository.save(token, 300);

            QueuePollingResult result = queueDomainService.getPollingResult(1L, TPS);

            assertAll(
                () -> assertThat(result.status()).isEqualTo(QueueStatus.TOKEN_ISSUED),
                () -> assertThat(result.token()).isNotNull(),
                () -> assertThat(result.token().token()).isEqualTo("test-token"),
                () -> assertThat(result.position()).isNull()
            );
        }

        @DisplayName("토큰이 만료되었으면, TOKEN_EXPIRED 상태를 반환한다.")
        @Test
        void returnsTokenExpired_whenTokenWasIssuedButExpired() {
            entryTokenRepository.saveStatus(1L, QueueStatus.TOKEN_ISSUED.name(), 420);

            QueuePollingResult result = queueDomainService.getPollingResult(1L, TPS);

            assertAll(
                () -> assertThat(result.status()).isEqualTo(QueueStatus.TOKEN_EXPIRED),
                () -> assertThat(result.token()).isNull(),
                () -> assertThat(result.position()).isNull()
            );
        }

        @DisplayName("대기열에도 없고 토큰도 없으면, NOT_IN_QUEUE 상태를 반환한다.")
        @Test
        void returnsNotInQueue_whenNeitherInQueueNorHasToken() {
            QueuePollingResult result = queueDomainService.getPollingResult(999L, TPS);

            assertAll(
                () -> assertThat(result.status()).isEqualTo(QueueStatus.NOT_IN_QUEUE),
                () -> assertThat(result.token()).isNull(),
                () -> assertThat(result.position()).isNull()
            );
        }

        @DisplayName("토큰 소비 후 polling하면, NOT_IN_QUEUE 상태를 반환한다.")
        @Test
        void returnsNotInQueue_afterTokenConsumed() {
            EntryToken token = new EntryToken(1L, "test-token", System.currentTimeMillis());
            entryTokenRepository.save(token, 300);
            entryTokenRepository.saveStatus(1L, QueueStatus.TOKEN_ISSUED.name(), 420);

            entryTokenRepository.findAndDeleteByUserId(1L);

            QueuePollingResult result = queueDomainService.getPollingResult(1L, TPS);
            assertThat(result.status()).isEqualTo(QueueStatus.NOT_IN_QUEUE);
        }
    }
}
