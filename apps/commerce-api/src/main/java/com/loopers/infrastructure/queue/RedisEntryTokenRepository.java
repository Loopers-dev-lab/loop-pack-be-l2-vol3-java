package com.loopers.infrastructure.queue;

import com.loopers.config.redis.RedisConfig;
import com.loopers.domain.queue.EntryTokenRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * 입장(대기열 통과) 자격 토큰을 Redis 문자열로 저장한다.
 * 키는 사용자당 하나({@code queue:entry:{userId}})이며, TTL 만료 시 자동으로 입장 자격이 소멸한다.
 */
@Repository
public class RedisEntryTokenRepository implements EntryTokenRepository {

    /** Redis 키 접두사. userId와 조합해 사용자별 입장 토큰을 구분한다. */
    private static final String ENTRY_TOKEN_KEY_PREFIX = "queue:entry:";

    private static final DefaultRedisScript<Long> CONSUME_IF_MATCHES_SCRIPT = new DefaultRedisScript<>(
            """
                    local v = redis.call('GET', KEYS[1])
                    if not v then return 0 end
                    if v == ARGV[1] then
                      redis.call('DEL', KEYS[1])
                      return 1
                    end
                    return 2
                    """,
            Long.class
    );

    private final RedisTemplate<String, String> redisTemplate;

    public RedisEntryTokenRepository(
        @Qualifier(RedisConfig.REDIS_TEMPLATE_MASTER) RedisTemplate<String, String> redisTemplate
    ) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * 사용자별 입장 토큰을 덮어쓰며 TTL을 설정한다. 스케줄러가 배치로 발급할 때 호출된다.
     */
    @Override
    public void saveEntryToken(Long userId, String token, long ttlSeconds) {
        redisTemplate.opsForValue().set(entryTokenKey(userId), token, ttlSeconds, TimeUnit.SECONDS);
    }

    /**
     * 입장 검증 등에서 현재 저장된 토큰 문자열을 조회한다. 없거나 만료되면 empty.
     */
    @Override
    public Optional<String> findEntryToken(Long userId) {
        return Optional.ofNullable(redisTemplate.opsForValue().get(entryTokenKey(userId)));
    }

    /**
     * 입장 소비 후 자격을 제거할 때 사용한다.
     */
    @Override
    public void deleteEntryToken(Long userId) {
        redisTemplate.delete(entryTokenKey(userId));
    }

    /**
     * GET·DEL을 Lua로 묶어 동시에 두 요청이 같은 토큰을 통과하지 못하게 한다.
     */
    @Override
    public boolean consumeIfTokenMatches(Long userId, String presentedToken) {
        Objects.requireNonNull(presentedToken, "presentedToken");
        Long result = redisTemplate.execute(
                CONSUME_IF_MATCHES_SCRIPT,
                List.of(entryTokenKey(userId)),
                presentedToken
        );
        return Long.valueOf(1L).equals(result);
    }

    private String entryTokenKey(Long userId) {
        return ENTRY_TOKEN_KEY_PREFIX + userId;
    }
}
