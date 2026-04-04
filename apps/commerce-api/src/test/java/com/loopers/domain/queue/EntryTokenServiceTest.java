package com.loopers.domain.queue;

import com.loopers.config.QueueProperties;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("EntryTokenService 단위 테스트")
class EntryTokenServiceTest {

    private EntryTokenRepository entryTokenRepository;
    private EntryTokenService entryTokenService;

    @BeforeEach
    void setUp() {
        entryTokenRepository = mock(EntryTokenRepository.class);
        QueueProperties queueProperties = new QueueProperties(true, 14, 100, 300, 140, 100, 2);
        entryTokenService = new EntryTokenService(entryTokenRepository, queueProperties);
    }

    @Nested
    @DisplayName("issue()")
    class IssueTest {

        @Test
        @DisplayName("UUID 토큰 발급 및 Repository에 저장")
        void issue_generatesUuidAndStores() {
            // when
            String token = entryTokenService.issue(1L);

            // then
            assertThat(token).isNotNull().isNotEmpty();
            verify(entryTokenRepository).issue(eq(1L), eq(token), eq(300));
        }
    }

    @Nested
    @DisplayName("validate()")
    class ValidateTest {

        @Test
        @DisplayName("유효한 토큰이면 예외 없이 통과")
        void validate_validToken_noException() {
            // given
            when(entryTokenRepository.findToken(1L)).thenReturn(Optional.of("valid-token"));

            // when & then — 예외 없음
            entryTokenService.validate(1L, "valid-token");
        }

        @Test
        @DisplayName("토큰 없으면 QUEUE_TOKEN_REQUIRED 예외")
        void validate_noToken_throwsRequired() {
            // given
            when(entryTokenRepository.findToken(1L)).thenReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> entryTokenService.validate(1L, "any-token"))
                    .isInstanceOf(CoreException.class)
                    .extracting("errorType")
                    .isEqualTo(ErrorType.QUEUE_TOKEN_REQUIRED);
        }

        @Test
        @DisplayName("토큰 불일치 시 QUEUE_TOKEN_INVALID 예외")
        void validate_mismatchToken_throwsInvalid() {
            // given
            when(entryTokenRepository.findToken(1L)).thenReturn(Optional.of("real-token"));

            // when & then
            assertThatThrownBy(() -> entryTokenService.validate(1L, "wrong-token"))
                    .isInstanceOf(CoreException.class)
                    .extracting("errorType")
                    .isEqualTo(ErrorType.QUEUE_TOKEN_INVALID);
        }
    }

    @Nested
    @DisplayName("consume()")
    class ConsumeTest {

        @Test
        @DisplayName("Repository consume 위임")
        void consume_delegatesToRepository() {
            // when
            entryTokenService.consume(1L);

            // then
            verify(entryTokenRepository).consume(1L);
        }
    }
}
