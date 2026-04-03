package com.loopers.infrastructure.user;

import com.loopers.domain.user.UserAuthCacheRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Repository
@RequiredArgsConstructor
public class RedisUserAuthRepository implements UserAuthCacheRepository {

    private static final String KEY_PREFIX = "auth:cache:";
    private static final long TTL_SECONDS = 30 * 60L; // 30분

    // 인증 캐시는 읽기 부하가 압도적 → Replica 읽기 활용
    private final RedisTemplate<String, String> redisTemplateReplica;
    private final RedisTemplate<String, String> redisTemplateMaster;

    @Override
    public Optional<CachedAuth> find(String loginId) {
        String raw = redisTemplateReplica.opsForValue().get(KEY_PREFIX + loginId);
        if (raw == null) {
            return Optional.empty();
        }
        return Optional.of(CachedAuth.deserialize(raw));
    }

    @Override
    public void save(String loginId, CachedAuth cachedAuth) {
        redisTemplateMaster.opsForValue()
                .set(KEY_PREFIX + loginId, cachedAuth.serialize(), TTL_SECONDS, TimeUnit.SECONDS);
    }

    @Override
    public void evict(String loginId) {
        redisTemplateMaster.delete(KEY_PREFIX + loginId);
    }
}
