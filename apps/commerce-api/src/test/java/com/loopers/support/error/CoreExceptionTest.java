package com.loopers.support.error;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CoreExceptionTest {
    @DisplayName("커스텀 메시지가 없으면 ErrorType의 메시지를 사용한다")
    @Test
    void usesErrorTypeMessageByDefault() {
        // arrange
        ErrorType[] errorTypes = ErrorType.values();

        // act & assert
        for (ErrorType errorType : errorTypes) {
            CoreException exception = new CoreException(errorType);
            assertThat(exception.getMessage()).isEqualTo(errorType.getMessage());
        }
    }

    @DisplayName("커스텀 메시지가 주어지면 해당 메시지를 사용한다")
    @Test
    void usesCustomMessageWhenProvided() {
        // arrange
        String customMessage = "custom message";

        // act
        CoreException exception = new CoreException(ErrorType.INTERNAL_ERROR, customMessage);

        // assert
        assertThat(exception.getMessage()).isEqualTo(customMessage);
    }
}
