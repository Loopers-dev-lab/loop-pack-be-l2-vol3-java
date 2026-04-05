package com.loopers.domain.queue;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

// 입장 토큰의 발급, 조회, 삭제를 담당하는 도메인 서비스.
// 토큰은 대기열을 통과한 유저가 주문 API에 접근할 수 있는 일회성 인증 수단이다.
// UUID 기반으로 생성되며, TTL(5분) 이후 자동 만료되어 미사용 토큰을 정리한다.
@RequiredArgsConstructor
@Service
public class QueueTokenService {

    // 토큰 유효 시간. 5분 이내에 주문을 완료하지 않으면 토큰이 만료되어 재대기가 필요하다.
    private static final long TOKEN_TTL_SECONDS = 300;

    private final QueueTokenRepository queueTokenRepository;

    // 유저에게 입장 토큰을 발급한다.
    // NX 옵션으로 이미 토큰이 있는 유저에게는 중복 발급하지 않으며, 이 경우 Optional.empty()를 반환한다.
    // 스케줄러(QueueScheduler)에서 대기열 앞쪽 유저에게 순차적으로 호출한다.
    public Optional<String> issueToken(Long userId) {
        String token = UUID.randomUUID().toString();
        boolean issued = queueTokenRepository.issue(userId, token, TOKEN_TTL_SECONDS);
        return issued ? Optional.of(token) : Optional.empty();
    }

    // 유저에게 유효한 토큰이 존재하는지 확인한다.
    public boolean hasToken(Long userId) {
        return queueTokenRepository.hasToken(userId);
    }

    // 유저의 토큰을 원자적으로 소모한다 (GETDEL).
    // 토큰 값을 반환하면서 동시에 삭제하여, 동시 요청 시 하나만 성공하도록 보장한다.
    // QueueTokenInterceptor.preHandle에서 주문 API 진입 시 호출된다.
    public Optional<String> consumeToken(Long userId) {
        return queueTokenRepository.consumeToken(userId);
    }

    // 유저의 토큰을 조회한다. TTL 만료 시 Optional.empty()를 반환한다.
    // QueueService.getPosition()에서 토큰 발급 여부를 확인할 때 사용된다.
    public Optional<String> findToken(Long userId) {
        return queueTokenRepository.getToken(userId);
    }

    // 유저의 토큰을 명시적으로 삭제한다.
    // 주문 성공 후 QueueTokenInterceptor.afterCompletion()에서 호출하여 토큰을 회수한다.
    public void deleteToken(Long userId) {
        queueTokenRepository.delete(userId);
    }
}
