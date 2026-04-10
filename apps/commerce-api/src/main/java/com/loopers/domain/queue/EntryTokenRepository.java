package com.loopers.domain.queue;

public interface EntryTokenRepository {

    // SET NX EX — 토큰이 없는 경우에만 발급 (멱등성 보장)
    // true: 새로 발급, false: 이미 존재 (NX 실패)
    boolean issueIfAbsent(Long userId, String token, long ttlSeconds);

    // EXISTS — 토큰 존재 여부 확인
    boolean existsByUserId(Long userId);

    // DEL — 주문 완료 후 명시적 삭제
    void delete(Long userId);
}
