package com.loopers.domain.queue;

import com.loopers.config.redis.RedisConfig;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.UUID;

@RequiredArgsConstructor
@Component
public class EntryTokenService {

    private static final String TOKEN_KEY_PREFIX = "entry-token:";

    private final RedisTemplate<String, String> redisTemplate;

    @Qualifier(RedisConfig.REDIS_TEMPLATE_MASTER)
    private final RedisTemplate<String, String> masterRedisTemplate;

    private final QueueProperties queueProperties;

    /**
     * 입장 토큰 발급
     * SET entry-token:{userId} {uuid} EX {ttl}
     */
    public String issueToken(Long userId) {
        String token = UUID.randomUUID().toString();
        masterRedisTemplate.opsForValue().set(
                TOKEN_KEY_PREFIX + userId,
                token,
                Duration.ofSeconds(queueProperties.tokenTtlSeconds())
        );
        return token;
    }

    /**
     * 토큰 조회 없으면 null (만료됐거나 발급 안 됨)
     */
    public String getToken(Long userId) {
        return redisTemplate.opsForValue().get(TOKEN_KEY_PREFIX + userId);
    }

    /**
     * 토큰 검증 후 삭제 (주문 완료 시 호출)
     * GETDEL로 조회와 삭제를 원자적으로 수행한다.
     * - master에서 읽어야 replication lag으로 인한 잘못된 거부를 방지한다.
     * - GETDEL은 원자적이므로 동일 유저의 동시 주문 요청 시 하나만 통과한다.
     */
    public void validateAndDelete(Long userId) {
        String token = masterRedisTemplate.opsForValue().getAndDelete(TOKEN_KEY_PREFIX + userId);

        if (token == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "유효한 입장 토큰이 없습니다.");
        }
    }
}
