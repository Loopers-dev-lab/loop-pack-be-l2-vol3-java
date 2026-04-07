package com.loopers.domain.queue;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.springframework.stereotype.Component;

/**
 * 대기열에서 발급된 입장 자격(토큰)이 주문 요청에 제시되었는지 확인하고, 성공 시 한 번만 쓰이도록 소비한다.
 * <p>
 * 호출 경로는 보통 {@code com.loopers.application.order.OrderEntryTokenGate} → 본 서비스이며,
 * 애플리케이션에서 {@code queue.order.require-entry-token}이 켜진 경우에만 실행된다.
 * <p>
 * 일치 검증과 삭제는 {@link EntryTokenRepository#consumeIfTokenMatches} 한 번으로 묶인다(재사용·이중 주문 방지).
 */
@Component
public class OrderEntryTokenService {

    private final EntryTokenRepository entryTokenRepository;

    public OrderEntryTokenService(EntryTokenRepository entryTokenRepository) {
        this.entryTokenRepository = entryTokenRepository;
    }

    /**
     * {@code presentedToken}이 비어 있으면 즉시 {@code CoreException}({@link ErrorType#BAD_REQUEST})이다.
     * 그렇지 않으면 트림한 값이 해당 {@code userId}의 저장 토큰과 일치할 때만 Redis에서 소비(삭제)한다.
     * 없거나 불일치면 동일하게 {@link ErrorType#BAD_REQUEST}이다.
     *
     * @param userId         입장 자격이 묶인 유저
     * @param presentedToken 클라이언트가 보낸 원문
     */
    public void assertValidAndConsume(Long userId, String presentedToken) {
        if (presentedToken == null || presentedToken.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "X-Entry-Token 헤더가 필요합니다.");
        }
        String trimmed = presentedToken.trim();
        if (!entryTokenRepository.consumeIfTokenMatches(userId, trimmed)) {
            throw new CoreException(ErrorType.BAD_REQUEST, "입장 토큰이 없거나 유효하지 않습니다.");
        }
    }
}
