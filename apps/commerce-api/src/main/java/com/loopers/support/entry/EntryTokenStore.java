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
     * 사용자의 입장 토큰을 검증한다.
     *
     * <p>저장된 토큰과 요청 토큰을 비교하여 일치 여부를 확인한다.
     * 토큰이 없거나 불일치하면 {@code CoreException(INVALID_ENTRY_TOKEN)}을 던진다.</p>
     *
     * @param userId 사용자 ID
     * @param token  클라이언트가 전달한 진입 토큰
     */
    void validate(Long userId, String token);

    /**
     * 사용자의 입장 토큰을 삭제한다.
     *
     * <p>주문 성공 후 호출되어 토큰을 제거한다.
     * TTL 만료 전에 삭제되지 않은 토큰은 주문 미처리 유저로 모니터링된다.</p>
     *
     * @param userId 사용자 ID
     */
    void delete(Long userId);
}
