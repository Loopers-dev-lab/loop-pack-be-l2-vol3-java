package com.loopers.domain.order;

import com.loopers.domain.order.model.OrderCommand;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class OrderCommandTest {

    @DisplayName("GetByPeriod를 생성할 때, ")
    @Nested
    class GetByPeriod {

        @DisplayName("시작일이 종료일보다 이전이면 정상 생성된다.")
        @Test
        void success() {
            // arrange
            Long memberId = 1L;
            LocalDateTime startAt = LocalDateTime.of(2024, 1, 1, 0, 0);
            LocalDateTime endAt = LocalDateTime.of(2024, 12, 31, 23, 59);

            // act & assert
            assertDoesNotThrow(() -> new OrderCommand.GetByPeriod(memberId, startAt, endAt));
        }

        @DisplayName("시작일과 종료일이 모두 null이면 정상 생성된다.")
        @Test
        void success_whenBothNull() {
            // arrange
            Long memberId = 1L;

            // act & assert
            assertDoesNotThrow(() -> new OrderCommand.GetByPeriod(memberId, null, null));
        }

        @DisplayName("시작일이 종료일보다 이후이면 CoreException(BAD_REQUEST)이 발생한다.")
        @Test
        void throwsException_whenStartAfterEnd() {
            // arrange
            Long memberId = 1L;
            LocalDateTime startAt = LocalDateTime.of(2024, 12, 31, 23, 59);
            LocalDateTime endAt = LocalDateTime.of(2024, 1, 1, 0, 0);

            // act & assert
            assertThatThrownBy(() -> new OrderCommand.GetByPeriod(memberId, startAt, endAt))
                .isInstanceOf(CoreException.class)
                .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.BAD_REQUEST));
        }

        @DisplayName("시작일만 있고 종료일이 null이면 정상 생성된다")
        @Test
        void success_whenOnlyStartAtProvided() {
            // arrange
            Long memberId = 1L;
            LocalDateTime startAt = LocalDateTime.of(2024, 1, 1, 0, 0);

            // act & assert
            assertDoesNotThrow(() -> new OrderCommand.GetByPeriod(memberId, startAt, null));
        }

        @DisplayName("종료일만 있고 시작일이 null이면 정상 생성된다")
        @Test
        void success_whenOnlyEndAtProvided() {
            // arrange
            Long memberId = 1L;
            LocalDateTime endAt = LocalDateTime.of(2024, 12, 31, 23, 59);

            // act & assert
            assertDoesNotThrow(() -> new OrderCommand.GetByPeriod(memberId, null, endAt));
        }
    }
}
