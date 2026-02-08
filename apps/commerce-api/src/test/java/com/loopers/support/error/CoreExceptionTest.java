package com.loopers.support.error;

import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class CoreExceptionTest {

    @Test
    void 별도_메시지가_없으면_ErrorType의_메시지를_사용한다() {
        // arrange
        CommonErrorType[] errorTypes = CommonErrorType.values();

        // act & assert
        for (CommonErrorType errorType : errorTypes) {
            CoreException exception = new CoreException(errorType);
            assertThat(exception.getMessage()).isEqualTo(errorType.getMessage());
        }
    }

    @Test
    void 별도_메시지가_주어지면_해당_메시지를_사용한다() {
        // arrange
        String customMessage = "custom message";

        // act
        CoreException exception = new CoreException(CommonErrorType.INTERNAL_ERROR, customMessage);

        // assert
        assertThat(exception.getMessage()).isEqualTo(customMessage);
    }
}
