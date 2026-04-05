package com.loopers.interfaces.scheduler;

import com.loopers.application.queue.QueueFacade;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class QueueSchedulerTest {

    QueueFacade queueFacade = mock(QueueFacade.class);
    QueueScheduler scheduler = new QueueScheduler(queueFacade);

    @DisplayName("issueTokens() 호출 시, ")
    @Nested
    class IssueTokens {

        @DisplayName("queueFacade.issueTokens()를 호출한다.")
        @Test
        void delegatesTokenIssuing() {
            // act
            scheduler.issueTokens();

            // assert
            verify(queueFacade).issueTokens();
        }

        @DisplayName("토큰 발급 중 예외가 발생해도 예외를 전파하지 않는다.")
        @Test
        void doesNotThrow_whenTokenIssuingFails() {
            // arrange
            doThrow(new RuntimeException("fail")).when(queueFacade).issueTokens();

            // act & assert
            assertThatCode(() -> scheduler.issueTokens()).doesNotThrowAnyException();
            verify(queueFacade).issueTokens();
        }
    }
}
