package com.loopers.domain.queue;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.UUID;

/**
 * 입장 토큰 도메인 서비스. 토큰 발급, 검증, 조회를 담당한다.
 *
 * <p>스케줄러가 ZPOPMIN으로 꺼낸 유저에게 토큰을 발급하고,
 * Interceptor가 주문 시 토큰을 검증+소비한다.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EntryTokenService {

    private final EntryTokenRepository entryTokenRepository;
    private final QueueProperties queueProperties;

    /**
     * 토큰 발급. SET NX로 멱등 — 이미 있으면 기존 토큰 반환.
     *
     * @param userId 사용자 ID
     * @return 발급된 또는 기존 토큰 UUID
     */
    public String issueToken(Long userId) {
        String token = UUID.randomUUID().toString();
        Duration ttl = Duration.ofSeconds(queueProperties.getTokenTtlSeconds());

        // 1차 시도: SET NX
        boolean issued = entryTokenRepository.setIfAbsent(userId, token, ttl);
        if (issued) {
            log.debug("토큰 신규 발급: userId={}", userId);
            return token;
        }

        // 이미 토큰이 있음 → 기존 토큰 반환 시도
        String existing = entryTokenRepository.get(userId);
        if (existing != null) {
            log.debug("기존 토큰 반환: userId={}", userId);
            return existing;
        }

        // GET 사이에 TTL 만료됨 → 새 토큰으로 재발급 (2차 시도)
        log.debug("토큰 TTL 만료 감지, 재발급 시도: userId={}", userId);
        entryTokenRepository.setIfAbsent(userId, token, ttl);
        String finalToken = entryTokenRepository.get(userId);

        // 극히 드문 경우: 2차에서도 null → 방어적으로 생성한 토큰 반환
        return finalToken != null ? finalToken : token;
    }

    /**
     * 토큰 검증 + 원자적 삭제 (Lua script, 1회 사용 보장).
     *
     * @param userId 사용자 ID
     * @param token  검증할 토큰 값
     * @return true=유효 + 삭제됨, false=불일치 또는 만료
     */
    public boolean validateAndConsume(Long userId, String token) {
        return entryTokenRepository.validateAndDelete(userId, token);
    }

    /**
     * 토큰 검증만 수행 (삭제 없음). Interceptor preHandle에서 사용.
     *
     * <p>주문 성공 후 {@link #consume(Long)}으로 삭제한다.
     * 주문 실패 시 토큰이 유지되어 TTL 내 재시도가 가능하다.</p>
     *
     * @param userId 사용자 ID
     * @param token  검증할 토큰 값
     * @return true=검증 성공, false=불일치 또는 만료
     */
    public boolean validate(Long userId, String token) {
        return entryTokenRepository.validate(userId, token);
    }

    /**
     * 토큰 삭제 (소비). 주문 성공 확정 후 afterCompletion에서 호출.
     *
     * @param userId 사용자 ID
     */
    public void consume(Long userId) {
        entryTokenRepository.delete(userId);
    }

    /**
     * 토큰 조회 (순번 조회 시 READY 상태 판단용).
     *
     * @param userId 사용자 ID
     * @return 토큰 값 또는 null
     */
    public String getToken(Long userId) {
        return entryTokenRepository.get(userId);
    }

    /**
     * 토큰 존재 여부.
     *
     * @param userId 사용자 ID
     * @return true=토큰 존재
     */
    public boolean hasToken(Long userId) {
        return entryTokenRepository.get(userId) != null;
    }
}
