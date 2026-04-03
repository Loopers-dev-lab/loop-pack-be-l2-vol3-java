package com.loopers.infrastructure.queue;

import com.loopers.domain.member.Member;
import com.loopers.infrastructure.redis.EntryTokenRedisRepository;
import com.loopers.infrastructure.resilience.SlidingWindowRateLimiter;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
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
    private SlidingWindowRateLimiter fallbackRateLimiter;
    private SimpleMeterRegistry meterRegistry;
    private ProceedingJoinPoint joinPoint;

    @BeforeEach
    void setUp() {
        entryTokenRedisRepository = mock(EntryTokenRedisRepository.class);
        fallbackRateLimiter = mock(SlidingWindowRateLimiter.class);
        meterRegistry = new SimpleMeterRegistry();
        interceptor = new EntryTokenInterceptor(entryTokenRedisRepository, fallbackRateLimiter, meterRegistry);
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

    // --- Graceful Degradation 테스트 ---

    @DisplayName("Redis 장애 + Rate Limit 허용 → proceed 실행")
    @Test
    void validateEntryToken_redisFails_rateLimitAllows_proceeds() throws Throwable {
        Member member = mock(Member.class);
        when(member.getId()).thenReturn(1L);
        when(joinPoint.getArgs()).thenReturn(new Object[]{member});
        when(entryTokenRedisRepository.exists(1L))
            .thenThrow(new RuntimeException("Redis connection failed"));
        when(fallbackRateLimiter.tryAcquire()).thenReturn(true);
        when(joinPoint.proceed()).thenReturn("fallback-result");

        Object result = interceptor.validateEntryToken(joinPoint);

        assertThat(result).isEqualTo("fallback-result");
        verify(joinPoint).proceed();
        verify(entryTokenRedisRepository, never()).consume(anyLong());
        double fallbackCount = meterRegistry.counter("queue.token.fallback").count();
        assertThat(fallbackCount).isEqualTo(1.0);
    }

    @DisplayName("Redis 장애 + Rate Limit 초과 → TOO_MANY_REQUESTS")
    @Test
    void validateEntryToken_redisFails_rateLimitExceeded_throws429() {
        Member member = mock(Member.class);
        when(member.getId()).thenReturn(1L);
        when(joinPoint.getArgs()).thenReturn(new Object[]{member});
        when(entryTokenRedisRepository.exists(1L))
            .thenThrow(new RuntimeException("Redis connection failed"));
        when(fallbackRateLimiter.tryAcquire()).thenReturn(false);

        assertThatThrownBy(() -> interceptor.validateEntryToken(joinPoint))
            .isInstanceOf(CoreException.class)
            .satisfies(e -> assertThat(((CoreException) e).getErrorType())
                .isEqualTo(ErrorType.TOO_MANY_REQUESTS));
    }

    @DisplayName("consume 실패 → 정상 처리 (소비 무시)")
    @Test
    void validateEntryToken_consumeFails_proceedsNormally() throws Throwable {
        Member member = mock(Member.class);
        when(member.getId()).thenReturn(1L);
        when(joinPoint.getArgs()).thenReturn(new Object[]{member});
        when(entryTokenRedisRepository.exists(1L)).thenReturn(true);
        when(joinPoint.proceed()).thenReturn("result");
        doThrow(new RuntimeException("Redis connection failed"))
            .when(entryTokenRedisRepository).consume(1L);

        Object result = interceptor.validateEntryToken(joinPoint);

        assertThat(result).isEqualTo("result");
        verify(joinPoint).proceed();
    }
}
