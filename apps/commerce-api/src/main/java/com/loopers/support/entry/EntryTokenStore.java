package com.loopers.support.entry;

import java.util.Optional;

/**
 * 대기열 통과 후 발급되는 입장 토큰의 저장소 인터페이스.
 */
public interface EntryTokenStore {

    /**
     * 사용자의 입장 토큰을 조회한다.
     *
     * @param userId 사용자 ID
     * @return 토큰이 존재하면 토큰 값, 없으면 빈 Optional
     */
    Optional<String> getToken(Long userId);

    /**
     * 사용자의 입장 토큰을 검증하고 소멸시킨다.
     *
     * <p>Redis에서 토큰을 원자적으로 조회+삭제한 뒤, 요청 토큰과 비교한다.
     * 토큰이 없거나 불일치하면 {@code CoreException(INVALID_ENTRY_TOKEN)}을 던진다.</p>
     *
     * @param userId 사용자 ID
     * @param token  클라이언트가 전달한 진입 토큰
     */
    void validateAndConsume(Long userId, String token);
}
