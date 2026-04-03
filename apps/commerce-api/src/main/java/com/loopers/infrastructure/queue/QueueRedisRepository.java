package com.loopers.infrastructure.queue;

import com.loopers.domain.queue.QueueRepository;
import com.loopers.domain.queue.QueueToken;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

@RequiredArgsConstructor
@Repository
public class QueueRedisRepository implements QueueRepository {

    // queue-token:{token} → userId (폴링 시 userId 추출용)
    // TTL 1시간: 대기가 1시간 이상 걸리면 토큰 만료 → 재진입 유도
    private static final long TOKEN_TTL_SECONDS = 3600L;

    // entered:{userId} → "1" (입장 허가 여부, Service API 게이트키퍼)
    // TTL 5분: 입장 후 5분 안에 주문 완료하지 않으면 기회 소멸 → 다음 유저에게
    private static final long ENTERED_TTL_SECONDS = 300L;

    private final RedisTemplate<String, String> redisTemplate;

    /**
     * Lua 스크립트 — 입장 허가의 핵심.
     *
     * 왜 Lua인가:
     * ZREM과 SET 사이에 다른 요청이 끼어들면
     * "대기열에서 제거됐는데 entered에는 없는" 중간 상태 발생
     * → 정상 유저가 Service API에서 403으로 튕겨남.
     * Lua는 Redis 싱글 스레드를 이용해 이 틈을 원천 차단.
     * 네트워크 왕복도 3회 → 1회로 감소.
     *
     * KEYS[1] = 대기열 Sorted Set 키 (e.g. "queue:bf-2024")
     * KEYS[2] = entered 키 (e.g. "entered:{userId}")
     * ARGV[1] = userId
     * ARGV[2] = threshold (rank ≤ threshold이면 입장 허가)
     * ARGV[3] = entered TTL (초)
     *
     * 반환값:
     *  1 → 입장 허가됨 (ZREM + SET 완료)
     *  0 → 아직 순번 아님 (rank > threshold)
     * -1 → 대기열에 없음 (이미 처리됐거나 잘못된 요청)
     */
    private static final DefaultRedisScript<Long> ADMIT_SCRIPT = new DefaultRedisScript<>("""
        local rank = redis.call('ZRANK', KEYS[1], ARGV[1])
        if rank == false then
            return -1
        end
        if tonumber(rank) > tonumber(ARGV[2]) then
            return 0
        end
        redis.call('ZREM', KEYS[1], ARGV[1])
        redis.call('SET', KEYS[2], '1', 'EX', ARGV[3])
        return 1
        """, Long.class);

    @Override
    public void enter(QueueToken token) {
        // Sorted Set: score = joinedAt → 먼저 들어온 사람이 앞 순번 (FIFO)
        // 재진입 시 score만 업데이트 → 후순위로 밀림 (공정성 유지)
        redisTemplate.opsForZSet().add(
            queueKey(token.queueId()),
            String.valueOf(token.userId()),
            token.joinedAt()
        );

        // 폴링 토큰 저장: 클라이언트가 token으로 userId를 조회할 수 있게
        redisTemplate.opsForValue().set(
            tokenKey(token.token()),
            String.valueOf(token.userId()),
            TOKEN_TTL_SECONDS,
            TimeUnit.SECONDS
        );
    }

    @Override
    public Optional<Long> getUserIdByToken(String token) {
        String userId = redisTemplate.opsForValue().get(tokenKey(token));
        if (userId == null) return Optional.empty();
        return Optional.of(Long.parseLong(userId));
    }

    @Override
    public Optional<Long> getRank(String queueId, Long userId) {
        // ZRANK O(log N): 10만 명에서도 마이크로초 응답
        Long rank = redisTemplate.opsForZSet().rank(queueKey(queueId), String.valueOf(userId));
        return Optional.ofNullable(rank);
    }

    @Override
    public long getTotalSize(String queueId) {
        // ZCARD O(1)
        Long size = redisTemplate.opsForZSet().size(queueKey(queueId));
        return size == null ? 0L : size;
    }

    @Override
    public boolean admit(String queueId, Long userId, long threshold) {
        Long result = redisTemplate.execute(
            ADMIT_SCRIPT,
            List.of(queueKey(queueId), enteredKey(userId)),
            String.valueOf(userId),
            String.valueOf(threshold),
            String.valueOf(ENTERED_TTL_SECONDS)
        );
        return result != null && result == 1L;
    }

    @Override
    public boolean isEntered(Long userId) {
        // EXISTS entered:{userId}: 없으면 Service API가 403 반환
        return Boolean.TRUE.equals(redisTemplate.hasKey(enteredKey(userId)));
    }

    // --- 키 네이밍 컨벤션 ---
    // 한 곳에서 관리해야 오타/불일치 방지
    private String queueKey(String queueId)  { return "queue:" + queueId; }
    private String tokenKey(String token)    { return "queue-token:" + token; }
    private String enteredKey(Long userId)   { return "entered:" + userId; }
}
