package com.loopers.domain.queue;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderEntryTokenServiceTest {

    private static final Long USER_ID = 99L;

    @Mock
    private EntryTokenRepository entryTokenRepository;

    @InjectMocks
    private OrderEntryTokenService orderEntryTokenService;

    @Nested
    @DisplayName("assertValidAndConsume")
    class AssertValidAndConsume {

        @Test
        @DisplayName("토큰이 null이면 BAD_REQUEST")
        void whenTokenNull_shouldThrowBadRequest() {
            assertThatThrownBy(() -> orderEntryTokenService.assertValidAndConsume(USER_ID, null))
                    .isInstanceOfSatisfying(CoreException.class,
                            ex -> assertThat(ex.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST));
        }

        @Test
        @DisplayName("토큰이 공백이면 BAD_REQUEST")
        void whenTokenBlank_shouldThrowBadRequest() {
            assertThatThrownBy(() -> orderEntryTokenService.assertValidAndConsume(USER_ID, "  "))
                    .isInstanceOfSatisfying(CoreException.class,
                            ex -> assertThat(ex.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST));
        }

        @Test
        @DisplayName("Redis에 토큰이 없거나 불일치하면 BAD_REQUEST")
        void whenConsumeFails_shouldThrowBadRequest() {
            when(entryTokenRepository.consumeIfTokenMatches(eq(USER_ID), eq("bad"))).thenReturn(false);

            assertThatThrownBy(() -> orderEntryTokenService.assertValidAndConsume(USER_ID, "bad"))
                    .isInstanceOfSatisfying(CoreException.class,
                            ex -> assertThat(ex.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST));
        }

        @Test
        @DisplayName("일치하면 소비하고 예외 없음 (앞뒤 공백은 trim)")
        void whenConsumeSucceeds_shouldNotThrow() {
            when(entryTokenRepository.consumeIfTokenMatches(eq(USER_ID), eq("ok"))).thenReturn(true);

            orderEntryTokenService.assertValidAndConsume(USER_ID, "  ok  ");

            verify(entryTokenRepository).consumeIfTokenMatches(USER_ID, "ok");
        }
    }
}
