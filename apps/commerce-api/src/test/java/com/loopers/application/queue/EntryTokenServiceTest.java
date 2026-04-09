package com.loopers.application.queue;

import com.loopers.domain.queue.InMemoryEntryTokenRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EntryTokenServiceTest {

    private InMemoryEntryTokenRepository entryTokenRepository;
    private EntryTokenService entryTokenService;

    @BeforeEach
    void setUp() {
        entryTokenRepository = new InMemoryEntryTokenRepository();
        entryTokenService = new EntryTokenService(entryTokenRepository);
    }

    @DisplayName("토큰 검증 및 소비 시, ")
    @Nested
    class ValidateAndConsume {

        @DisplayName("유효한 토큰이면 소비되고 정상 통과한다.")
        @Test
        void consumesToken_whenValid() {
            // arrange
            entryTokenRepository.issueToken(1L, "valid-token", Duration.ofMinutes(5));

            // act
            entryTokenService.validateAndConsume(1L, "valid-token");

            // assert
            assertThat(entryTokenRepository.getToken(1L)).isEmpty();
        }

        @DisplayName("토큰이 null이면 예외가 발생한다.")
        @Test
        void throwsException_whenTokenIsNull() {
            // act & assert
            assertThatThrownBy(() -> entryTokenService.validateAndConsume(1L, null))
                    .isInstanceOf(CoreException.class)
                    .extracting(e -> ((CoreException) e).getErrorType())
                    .isEqualTo(ErrorType.ENTRY_TOKEN_REQUIRED);
        }

        @DisplayName("토큰이 일치하지 않으면 예외가 발생한다.")
        @Test
        void throwsException_whenTokenMismatch() {
            // arrange
            entryTokenRepository.issueToken(1L, "valid-token", Duration.ofMinutes(5));

            // act & assert
            assertThatThrownBy(() -> entryTokenService.validateAndConsume(1L, "wrong-token"))
                    .isInstanceOf(CoreException.class)
                    .extracting(e -> ((CoreException) e).getErrorType())
                    .isEqualTo(ErrorType.ENTRY_TOKEN_INVALID);
        }

        @DisplayName("토큰이 만료되었으면 예외가 발생한다.")
        @Test
        void throwsException_whenTokenExpired() {
            // arrange: 음수 TTL로 즉시 만료
            entryTokenRepository.issueToken(1L, "expired-token", Duration.ofMillis(-1));

            // act & assert
            assertThatThrownBy(() -> entryTokenService.validateAndConsume(1L, "expired-token"))
                    .isInstanceOf(CoreException.class)
                    .extracting(e -> ((CoreException) e).getErrorType())
                    .isEqualTo(ErrorType.ENTRY_TOKEN_INVALID);
        }

        @DisplayName("이미 소비된 토큰은 재사용할 수 없다.")
        @Test
        void throwsException_whenAlreadyConsumed() {
            // arrange
            entryTokenRepository.issueToken(1L, "token", Duration.ofMinutes(5));
            entryTokenService.validateAndConsume(1L, "token");

            // act & assert
            assertThatThrownBy(() -> entryTokenService.validateAndConsume(1L, "token"))
                    .isInstanceOf(CoreException.class)
                    .extracting(e -> ((CoreException) e).getErrorType())
                    .isEqualTo(ErrorType.ENTRY_TOKEN_INVALID);
        }
    }
}
