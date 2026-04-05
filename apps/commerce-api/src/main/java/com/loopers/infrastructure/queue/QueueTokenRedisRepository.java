package com.loopers.infrastructure.queue;

import com.loopers.domain.queue.QueueConstants;
import com.loopers.domain.queue.QueueTokenRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.Optional;

import static com.loopers.config.redis.RedisConfig.REDIS_TEMPLATE_MASTER;

// Redis String을 이용한 입장 토큰 저장소 구현체.
// key: "entry-token:{userId}", value: UUID 토큰 문자열로 구성된다.
// SET NX + TTL로 원자적 토큰 발급과 자동 만료를 보장한다.
// Master 전용 RedisTemplate을 주입받아 쓰기 작업의 일관성을 보장한다.
@Repository
public class QueueTokenRedisRepository implements QueueTokenRepository {

    private static final String TOKEN_KEY_PREFIX = QueueConstants.TOKEN_KEY_PREFIX;

    private final RedisTemplate<String, String> redisTemplate;

    public QueueTokenRedisRepository(
            @Qualifier(REDIS_TEMPLATE_MASTER) RedisTemplate<String, String> redisTemplate
    ) {
        this.redisTemplate = redisTemplate;
    }

    // SET NX EX: key가 없을 때만 토큰을 저장하고 TTL을 설정한다.
    // 원자적 연산으로 동시에 여러 스레드가 같은 유저에게 토큰을 발급하려 해도 하나만 성공한다.
    @Override
    public boolean issue(Long userId, String token, long ttlSeconds) {
        Boolean result = redisTemplate.opsForValue()
                .setIfAbsent(tokenKey(userId), token, Duration.ofSeconds(ttlSeconds));
        return Boolean.TRUE.equals(result);
    }

    // GET: 토큰 값을 조회한다. TTL 만료 시 Redis가 자동 삭제하므로 null이 반환된다.
    @Override
    public Optional<String> getToken(Long userId) {
        String token = redisTemplate.opsForValue().get(tokenKey(userId));
        return Optional.ofNullable(token);
    }

    // EXISTS: 토큰 key의 존재 여부만 확인한다.
    // QueueTokenInterceptor에서 주문 API 접근 권한 검증에 사용된다.
    @Override
    public boolean hasToken(Long userId) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(tokenKey(userId)));
    }

    // GETDEL: 토큰을 원자적으로 조회하면서 삭제한다.
    // 동시 요청 시 하나만 토큰 값을 받고, 나머지는 empty를 받아 1회성 사용을 보장한다.
    @Override
    public Optional<String> consumeToken(Long userId) {
        String token = redisTemplate.opsForValue().getAndDelete(tokenKey(userId));
        return Optional.ofNullable(token);
    }

    // DEL: 토큰을 명시적으로 삭제한다.
    @Override
    public void delete(Long userId) {
        redisTemplate.delete(tokenKey(userId));
    }

    private String tokenKey(Long userId) {
        return TOKEN_KEY_PREFIX + userId;
    }
}
