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

    @DisplayName("토큰 검증 시, ")
    @Nested
    class Validate {

        @DisplayName("유효한 토큰이면 정상 통과한다.")
        @Test
        void passes_whenValidToken() {
            // arrange
            entryTokenRepository.issueToken(1L, "valid-token", Duration.ofMinutes(5));

            // act & assert: 예외 없이 통과
            entryTokenService.validate(1L, "valid-token");
        }

        @DisplayName("토큰이 null이면 예외가 발생한다.")
        @Test
        void throwsException_whenTokenIsNull() {
            // act & assert
            assertThatThrownBy(() -> entryTokenService.validate(1L, null))
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
            assertThatThrownBy(() -> entryTokenService.validate(1L, "wrong-token"))
                    .isInstanceOf(CoreException.class)
                    .extracting(e -> ((CoreException) e).getErrorType())
                    .isEqualTo(ErrorType.ENTRY_TOKEN_INVALID);
        }

        @DisplayName("토큰이 만료되었으면 예외가 발생한다.")
        @Test
        void throwsException_whenTokenExpired() {
            // arrange: 음수 TTL로 즉시 만료
            entryTokenRepository.issueToken(1L, "expired-token", Duration.ofMillis(-1));

            // act & assert: 토큰을 제공했지만 만료되어 유효하지 않음
            assertThatThrownBy(() -> entryTokenService.validate(1L, "expired-token"))
                    .isInstanceOf(CoreException.class)
                    .extracting(e -> ((CoreException) e).getErrorType())
                    .isEqualTo(ErrorType.ENTRY_TOKEN_INVALID);
        }
    }

    @DisplayName("토큰 소비 시, ")
    @Nested
    class Consume {

        @DisplayName("토큰이 삭제된다.")
        @Test
        void deletesToken() {
            // arrange
            entryTokenRepository.issueToken(1L, "token", Duration.ofMinutes(5));

            // act
            entryTokenService.consume(1L);

            // assert
            assertThat(entryTokenRepository.getToken(1L)).isEmpty();
        }
    }
}
