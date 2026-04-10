package com.loopers.domain.user;

import java.util.Optional;

public interface UserAuthCacheRepository {

    // loginId → (userId:encodedPassword) 조회
    Optional<CachedAuth> find(String loginId);

    // 인증 성공 후 캐싱 (TTL 30분)
    void save(String loginId, CachedAuth cachedAuth);

    // 비밀번호 변경 시 캐시 무효화
    void evict(String loginId);

    // BCrypt(≈100ms)를 캐시 경로에서 제거 — HMAC-SHA256(≈10μs)으로 대체
    // passwordFastHash = HMAC-SHA256(rawPassword, serverSecret) — Redis 유출 시 rainbow table 방어
    record CachedAuth(String passwordFastHash, Long userId, String name, String birthday, String email) {

        private static final String SEP = "||";

        public String serialize() {
            return passwordFastHash + SEP + userId + SEP + name + SEP + birthday + SEP + email;
        }

        public static CachedAuth deserialize(String raw) {
            String[] parts = raw.split("\\|\\|", 5);
            return new CachedAuth(parts[0], Long.parseLong(parts[1]), parts[2], parts[3], parts[4]);
        }
    }
}
