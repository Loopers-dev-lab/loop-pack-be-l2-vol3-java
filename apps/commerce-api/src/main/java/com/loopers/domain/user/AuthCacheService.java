package com.loopers.domain.user;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.support.error.CoreException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;

/**
 * 인증 결과를 Redis에 캐시하여 Polling 인증 DB 부하를 줄인다.
 *
 * <p>캐시에 인증에 필요한 최소 정보({@link AuthUserInfo})를 JSON 직렬화하여 저장한다.
 * {@link UserModel}(JPA Entity)을 직접 직렬화하면 프록시/지연 로딩 문제가 발생하므로,
 * 별도 record로 분리한다.</p>
 *
 * <p>Redis 장애 시 DB fallback으로 동작한다 (try-catch).</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthCacheService {

    private final RedisTemplate<String, String> redisTemplateMaster;
    private final UserService userService;
    private final ObjectMapper objectMapper;

    private static final Duration AUTH_CACHE_TTL = Duration.ofSeconds(30);
    private static final String KEY_PREFIX = "auth:cache:";

    /**
     * 캐시에 저장할 인증 정보. JPA Entity 직접 직렬화를 피하기 위한 경량 DTO.
     */
    public record AuthUserInfo(Long userId, String loginId, String userName) {
        public static AuthUserInfo from(UserModel user) {
            return new AuthUserInfo(user.getUserId(), user.getLoginId(), user.getUserName());
        }
    }

    /**
     * 인증 결과 캐시 조회 → 미스 시 DB 조회 후 캐시.
     *
     * <p>캐시 키 = {@code auth:cache:{loginId}:{sha256(password)}}.
     * passwordHash를 키에 포함하여 비밀번호 변경 시 자동 캐시 미스 발생.</p>
     *
     * <p>캐시 히트 시 → findByUserId(PK 조회, ~1ms).
     * 캐시 미스 시 → authenticate(loginId+password 조회+비교, ~5ms).</p>
     *
     * @param loginId     로그인 ID
     * @param rawPassword 평문 비밀번호
     * @return 인증된 UserModel
     * @throws CoreException UNAUTHORIZED 인증 실패
     */
    public UserModel authenticateWithCache(String loginId, String rawPassword) {
        String passwordHash = sha256Hex(rawPassword);
        String compositeKey = KEY_PREFIX + loginId + ":" + passwordHash;

        // 1. 캐시 히트 체크 — Redis 장애 시 DB fallback
        try {
            String cachedJson = redisTemplateMaster.opsForValue().get(compositeKey);
            if (cachedJson != null) {
                AuthUserInfo info = objectMapper.readValue(cachedJson, AuthUserInfo.class);
                return userService.findByUserId(info.userId());
            }
        } catch (Exception e) {
            log.warn("인증 캐시 조회 실패, DB fallback: {}", e.getMessage());
        }

        // 2. 캐시 미스 → DB 인증 (비밀번호 검증 포함)
        UserModel user = userService.authenticate(loginId, rawPassword);

        // 3. 인증 성공 시 캐시 저장 — Redis 장애 시 무시
        try {
            String json = objectMapper.writeValueAsString(AuthUserInfo.from(user));
            redisTemplateMaster.opsForValue().set(compositeKey, json, AUTH_CACHE_TTL);
        } catch (Exception e) {
            log.warn("인증 캐시 저장 실패: {}", e.getMessage());
        }

        return user;
    }

    private static String sha256Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
