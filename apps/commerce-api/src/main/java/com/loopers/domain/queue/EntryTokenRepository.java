package com.loopers.domain.queue;

import java.time.Duration;

/**
 * 입장 토큰 저장소 인터페이스. Redis String + TTL 기반으로 infrastructure에서 구현.
 *
 * <p>기존 {@code PaymentLock} 인터페이스와 동일한 DIP 패턴.
 * 도메인 레이어는 Redis에 의존하지 않는다.</p>
 */
public interface EntryTokenRepository {

    /**
     * 토큰을 저장한다. 이미 존재하면 무시 (SET NX EX).
     *
     * @param userId 사용자 ID
     * @param token  UUID 토큰 값
     * @param ttl    만료 시간
     * @return true=신규 저장, false=이미 존재
     */
    boolean setIfAbsent(Long userId, String token, Duration ttl);

    /**
     * 토큰을 조회한다 (GET). 없으면 null.
     *
     * @param userId 사용자 ID
     * @return 토큰 값 또는 null
     */
    String get(Long userId);

    /**
     * 토큰을 삭제한다 (DEL).
     *
     * @param userId 사용자 ID
     * @return true=삭제됨, false=이미 없음
     */
    boolean delete(Long userId);

    /**
     * 토큰을 검증하고 원자적으로 삭제한다 (Lua: GET → 비교 → DEL).
     *
     * <p>동시 요청 2건 중 정확히 1건만 성공한다 (1회 사용 보장).</p>
     *
     * @param userId 사용자 ID
     * @param token  검증할 토큰 값
     * @return true=검증 성공 + 삭제됨, false=불일치 또는 없음
     */
    boolean validateAndDelete(Long userId, String token);

    /**
     * 토큰을 검증만 수행한다 (삭제하지 않음, Lua: GET → 비교).
     *
     * <p>Interceptor preHandle에서 검증 후, 주문 성공 시 afterCompletion에서 삭제하는 패턴에 사용.
     * 주문 실패 시 토큰이 유지되어 TTL 내 재시도가 가능하다.</p>
     *
     * @param userId 사용자 ID
     * @param token  검증할 토큰 값
     * @return true=검증 성공 (토큰 일치), false=불일치 또는 없음
     */
    boolean validate(Long userId, String token);
}
