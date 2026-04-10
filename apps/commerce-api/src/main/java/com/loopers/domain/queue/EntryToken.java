package com.loopers.domain.queue;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorMessage;
import com.loopers.support.error.ErrorType;
import java.util.UUID;

/**
 * 입장 토큰 도메인
 * - 대기열을 통과한 유저에게 발급되는 일회성 토큰이다.
 * - 주문 API 호출 시 X-Entry-Token 헤더로 검증된다.
 */
public class EntryToken {

    private final Long userId;
    private final String token;

    private EntryToken(Long userId, String token) {
        this.userId = userId;
        this.token = token;
    }

    public static EntryToken issue(Long userId) {
        validate(userId);
        return new EntryToken(userId, UUID.randomUUID().toString());
    }

    public static EntryToken of(Long userId, String token) {
        validate(userId);
        return new EntryToken(userId, token);
    }

    private static void validate(Long userId) {
        if (userId == null || userId <= 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, ErrorMessage.Queue.USER_ID_INVALID);
        }
    }

    public Long getUserId() {
        return userId;
    }

    public String getToken() {
        return token;
    }
}
