package com.loopers.domain.queue;

import java.util.Optional;

// 입장 토큰 저장소 인터페이스. Domain 레이어에 정의하여 DIP를 적용한다.
// 구현체(QueueTokenRedisRepository)에서 Redis String + TTL로 토큰을 관리한다.
// 토큰은 대기열 통과 후 주문 API 접근 권한을 증명하는 일회성 인증 수단이다.
public interface QueueTokenRepository {

    // 유저에게 입장 토큰을 발급한다. TTL을 설정하여 만료 시 자동 삭제된다.
    // NX 옵션: 이미 토큰이 있는 유저에게는 중복 발급하지 않는다. 성공 시 true 반환.
    boolean issue(Long userId, String token, long ttlSeconds);

    // 유저의 입장 토큰을 조회한다. TTL 만료 시 Optional.empty() 반환.
    Optional<String> getToken(Long userId);

    // 유저에게 유효한 입장 토큰이 존재하는지 확인한다.
    boolean hasToken(Long userId);

    // 유저의 입장 토큰을 명시적으로 삭제한다.
    // 주문 성공 후 인터셉터(QueueTokenInterceptor)에서 호출하여 토큰을 회수한다.
    void delete(Long userId);
}
