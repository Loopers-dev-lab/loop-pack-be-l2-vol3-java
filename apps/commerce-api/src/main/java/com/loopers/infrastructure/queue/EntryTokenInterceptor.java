package com.loopers.infrastructure.queue;

import com.loopers.domain.member.Member;
import com.loopers.infrastructure.redis.EntryTokenRedisRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

/**
 * 대기열 입장 토큰 검증 AOP.
 *
 * <p>{@code @RequireEntryToken} 어노테이션이 붙은 메서드 실행 전 토큰 존재 여부를 확인한다.
 * 성공 시에만 토큰을 소비하여 예외 발생 시 재시도가 가능하도록 한다.</p>
 *
 * @see com.loopers.support.auth.RequireEntryToken
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class EntryTokenInterceptor {

    private final EntryTokenRedisRepository entryTokenRedisRepository;

    @Around("@annotation(com.loopers.support.auth.RequireEntryToken)")
    public Object validateEntryToken(ProceedingJoinPoint joinPoint) throws Throwable {
        Long memberId = extractMemberIdFromArgs(joinPoint);

        if (!entryTokenRedisRepository.exists(memberId)) {
            log.warn("입장 토큰 없음 — 주문 거부: memberId={}", memberId);
            throw new CoreException(ErrorType.FORBIDDEN, "대기열 입장 토큰이 없습니다.");
        }

        Object result = joinPoint.proceed();

        entryTokenRedisRepository.consume(memberId);
        return result;
    }

    private Long extractMemberIdFromArgs(ProceedingJoinPoint joinPoint) {
        for (Object arg : joinPoint.getArgs()) {
            if (arg instanceof Member member) {
                return member.getId();
            }
        }
        throw new CoreException(ErrorType.INTERNAL_ERROR, "Member 인자를 찾을 수 없습니다.");
    }
}
