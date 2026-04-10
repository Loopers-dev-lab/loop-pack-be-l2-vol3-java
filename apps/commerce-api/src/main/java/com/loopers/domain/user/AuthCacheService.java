package com.loopers.domain.user;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 인증 결과를 Redis에 캐시하여 Polling 인증 DB 부하를 줄인다.
 *
 * <p>캐시에 인증에 필요한 최소 정보({@link AuthUserInfo})를 JSON 직렬화하여 저장한다.
 * {@link UserModel}(JPA Entity)을 직접 직렬화하면 프록시/지연 로딩 문제가 발생하므로,
 * 별도 record로 분리한다.</p>
 *
 * <p>Redis 장애 시 DB fallback으로 동작한다 (try-catch).</p>
 *
 * <p>읽기는 Replica 우선, 쓰기는 Master로 분산하여 대기열 ZADD/ZPOPMIN과의 경합을 줄인다.</p>
 */
@Slf4j
@Service
public class AuthCacheService {

    private final AuthCacheRepository authCacheRepository;
    private final UserService userService;
    private final ObjectMapper objectMapper;

    public AuthCacheService(
            AuthCacheRepository authCacheRepository,
            UserService userService,
            ObjectMapper objectMapper) {
        this.authCacheRepository = authCacheRepository;
        this.userService = userService;
        this.objectMapper = objectMapper;
    }

    private static final long AUTH_CACHE_TTL_BASE_SECONDS = 300;
    private static final long AUTH_CACHE_TTL_JITTER_SECONDS = 30;
    private static final String KEY_PREFIX = "auth:cache:";

    /** Singleflight: 같은 키에 대해 1개 스레드만 DB 조회, 나머지는 대기 후 캐시 재조회 */
    private final ConcurrentHashMap<String, ReentrantLock> inFlightLocks = new ConcurrentHashMap<>();

    /**
     * 캐시에 저장할 인증 정보. JPA Entity 직접 직렬화를 피하기 위한 경량 DTO.
     *
     * <p>캐시 HIT 시 DB 조회 없이 {@link UserModel}을 복원하기 위해
     * Controller에서 사용하는 모든 필드를 포함한다.</p>
     */
    public record AuthUserInfo(
            Long userId, String loginId, String userName,
            String birthday, String email, String address
    ) {
        public static AuthUserInfo from(UserModel user) {
            return new AuthUserInfo(
                    user.getUserId(), user.getLoginId(), user.getUserName(),
                    user.getBirthday(), user.getEmail(), user.getAddress()
            );
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

        // 1. 캐시 히트 체크 — 장애 시 null 반환으로 DB fallback
        UserModel cached = getFromCache(compositeKey);
        if (cached != null) {
            return cached;
        }

        // 2. 캐시 미스 → singleflight: 같은 키에 대해 1개 스레드만 DB 조회
        ReentrantLock lock = inFlightLocks.computeIfAbsent(compositeKey, k -> new ReentrantLock());
        lock.lock();
        try {
            // 2-1. 다른 스레드가 이미 캐시에 저장했을 수 있으므로 재조회
            UserModel rechecked = getFromCache(compositeKey);
            if (rechecked != null) {
                return rechecked;
            }

            // 2-2. DB 인증 (비밀번호 검증 포함)
            UserModel user = userService.authenticate(loginId, rawPassword);

            // 2-3. 인증 성공 시 캐시 저장 — 장애 시 무시
            try {
                String json = objectMapper.writeValueAsString(AuthUserInfo.from(user));
                authCacheRepository.set(compositeKey, json, ttlWithJitter());
            } catch (Exception e) {
                log.warn("인증 캐시 저장 실패: {}", e.getMessage());
            }

            return user;
        } finally {
            lock.unlock();
            inFlightLocks.remove(compositeKey);
        }
    }

    private UserModel getFromCache(String compositeKey) {
        try {
            String cachedJson = authCacheRepository.get(compositeKey);
            if (cachedJson != null) {
                AuthUserInfo info = objectMapper.readValue(cachedJson, AuthUserInfo.class);
                return UserModel.fromCachedAuth(info);
            }
        } catch (Exception e) {
            log.warn("캐시 조회/역직렬화 실패, DB fallback: {}", e.getMessage());
        }
        return null;
    }

    /**
     * 캐시 스탬피드 방지를 위해 TTL에 ±30초 jitter를 추가한다.
     * 동시 warm-up된 캐시가 동시에 만료되는 것을 방지.
     */
    private static Duration ttlWithJitter() {
        long jitter = ThreadLocalRandom.current()
                .nextLong(-AUTH_CACHE_TTL_JITTER_SECONDS, AUTH_CACHE_TTL_JITTER_SECONDS + 1);
        return Duration.ofSeconds(AUTH_CACHE_TTL_BASE_SECONDS + jitter);
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
