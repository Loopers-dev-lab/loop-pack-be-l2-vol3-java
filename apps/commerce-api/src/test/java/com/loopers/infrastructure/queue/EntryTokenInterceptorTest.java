package com.loopers.infrastructure.queue;

import com.loopers.domain.member.Member;
import com.loopers.infrastructure.redis.EntryTokenRedisRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.aspectj.lang.ProceedingJoinPoint;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class EntryTokenInterceptorTest {

    private EntryTokenInterceptor interceptor;
    private EntryTokenRedisRepository entryTokenRedisRepository;
    private ProceedingJoinPoint joinPoint;

    @BeforeEach
    void setUp() {
        entryTokenRedisRepository = mock(EntryTokenRedisRepository.class);
        interceptor = new EntryTokenInterceptor(entryTokenRedisRepository);
        joinPoint = mock(ProceedingJoinPoint.class);
    }

    @DisplayName("토큰 존재 → proceed 실행 후 토큰 소비")
    @Test
    void validateEntryToken_tokenExists_proceedsAndConsumes() throws Throwable {
        Member member = mock(Member.class);
        when(member.getId()).thenReturn(1L);
        when(joinPoint.getArgs()).thenReturn(new Object[]{member});
        when(entryTokenRedisRepository.exists(1L)).thenReturn(true);
        when(joinPoint.proceed()).thenReturn("result");

        Object result = interceptor.validateEntryToken(joinPoint);

        assertThat(result).isEqualTo("result");
        verify(joinPoint).proceed();
        verify(entryTokenRedisRepository).consume(1L);
    }

    @DisplayName("토큰 없음 → FORBIDDEN 예외")
    @Test
    void validateEntryToken_noToken_throwsForbidden() {
        Member member = mock(Member.class);
        when(member.getId()).thenReturn(1L);
        when(joinPoint.getArgs()).thenReturn(new Object[]{member});
        when(entryTokenRedisRepository.exists(1L)).thenReturn(false);

        assertThatThrownBy(() -> interceptor.validateEntryToken(joinPoint))
            .isInstanceOf(CoreException.class)
            .satisfies(e -> assertThat(((CoreException) e).getErrorType())
                .isEqualTo(ErrorType.FORBIDDEN));

        verify(entryTokenRedisRepository, never()).consume(anyLong());
    }

    @DisplayName("proceed 예외 시 토큰 미소비")
    @Test
    void validateEntryToken_proceedThrows_tokenNotConsumed() throws Throwable {
        Member member = mock(Member.class);
        when(member.getId()).thenReturn(1L);
        when(joinPoint.getArgs()).thenReturn(new Object[]{member});
        when(entryTokenRedisRepository.exists(1L)).thenReturn(true);
        when(joinPoint.proceed()).thenThrow(new RuntimeException("주문 실패"));

        assertThatThrownBy(() -> interceptor.validateEntryToken(joinPoint))
            .isInstanceOf(RuntimeException.class)
            .hasMessage("주문 실패");

        verify(entryTokenRedisRepository, never()).consume(anyLong());
    }

    @DisplayName("Member 인자 없음 → INTERNAL_ERROR 예외")
    @Test
    void validateEntryToken_noMemberArg_throwsInternalError() {
        when(joinPoint.getArgs()).thenReturn(new Object[]{"notAMember"});

        assertThatThrownBy(() -> interceptor.validateEntryToken(joinPoint))
            .isInstanceOf(CoreException.class)
            .satisfies(e -> assertThat(((CoreException) e).getErrorType())
                .isEqualTo(ErrorType.INTERNAL_ERROR));
    }
}
