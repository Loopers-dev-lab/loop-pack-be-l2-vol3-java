package com.loopers.application.queue;

import com.loopers.config.redis.RedisConfig;
import com.loopers.domain.user.UserModel;
import com.loopers.domain.user.UserService;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Component
public class QueueService {

    private static final String WAITING_QUEUE_KEY = "queue:waiting";
    private static final String WAITING_SEQ_KEY = "queue:waiting:seq";
    private static final String ACTIVE_USER_PREFIX = "queue:active:user:";
    private static final String ACTIVE_TOKEN_PREFIX = "queue:active:token:";

    private final RedisTemplate<String, String> redisTemplate;
    private final UserService userService;
    private final QueueThroughputPolicy throughputPolicy;
    private final Duration tokenTtl;

    public QueueService(
        @Qualifier(RedisConfig.REDIS_TEMPLATE_MASTER) RedisTemplate<String, String> redisTemplate,
        UserService userService,
        QueueThroughputPolicy throughputPolicy,
        @Value("${queue.token-ttl-seconds:300}") long tokenTtlSeconds
    ) {
        this.redisTemplate = redisTemplate;
        this.userService = userService;
        this.throughputPolicy = throughputPolicy;
        this.tokenTtl = Duration.ofSeconds(tokenTtlSeconds);
    }

    public QueuePositionInfo enter(String loginId, String password) {
        UserModel user = userService.getMyInfo(loginId, password);
        return enterByUserId(user.getId());
    }

    public QueuePositionInfo getPosition(String loginId, String password) {
        UserModel user = userService.getMyInfo(loginId, password);
        return getPositionByUserId(user.getId());
    }

    public QueuePositionInfo enterByUserId(Long userId) {
        String userIdValue = String.valueOf(userId);
        String token = redisTemplate.opsForValue().get(activeUserKey(userIdValue));
        if (token != null) {
            return entered(userId, token);
        }

        double score = nextWaitingScore();
        redisTemplate.opsForZSet().addIfAbsent(WAITING_QUEUE_KEY, userIdValue, score);
        return waiting(userId);
    }

    public QueuePositionInfo getPositionByUserId(Long userId) {
        String userIdValue = String.valueOf(userId);
        String token = redisTemplate.opsForValue().get(activeUserKey(userIdValue));
        if (token != null) {
            return entered(userId, token);
        }

        Long rank = redisTemplate.opsForZSet().rank(WAITING_QUEUE_KEY, userIdValue);
        if (rank == null) {
            return QueuePositionInfo.notInQueue(userId, getTotalWaitingCount());
        }

        long position = rank + 1;
        long totalWaiting = getTotalWaitingCount();
        long estimatedWaitSeconds = throughputPolicy.estimateWaitSeconds(position);
        int recommendedPollingSeconds = throughputPolicy.recommendPollingSeconds(position);
        return new QueuePositionInfo(
            userId,
            QueueStatus.WAITING,
            position,
            totalWaiting,
            estimatedWaitSeconds,
            null,
            recommendedPollingSeconds
        );
    }

    public long getTotalWaitingCount() {
        Long count = redisTemplate.opsForZSet().size(WAITING_QUEUE_KEY);
        return count != null ? count : 0L;
    }

    public Map<Long, String> admitNextBatch(int batchSize) {
        Set<ZSetOperations.TypedTuple<String>> popped = redisTemplate.opsForZSet().popMin(WAITING_QUEUE_KEY, batchSize);
        Map<Long, String> issuedTokens = new LinkedHashMap<>();
        if (popped == null || popped.isEmpty()) {
            return issuedTokens;
        }

        for (ZSetOperations.TypedTuple<String> tuple : popped) {
            if (tuple.getValue() == null) {
                continue;
            }
            String userIdValue = tuple.getValue();
            String existing = redisTemplate.opsForValue().get(activeUserKey(userIdValue));
            if (existing != null) {
                issuedTokens.put(Long.parseLong(userIdValue), existing);
                continue;
            }

            String token = issueToken(userIdValue);
            issuedTokens.put(Long.parseLong(userIdValue), token);
        }
        return issuedTokens;
    }

    public void validateTokenOrThrow(String loginId, String password, String token) {
        UserModel user = userService.getMyInfo(loginId, password);
        validateTokenOrThrow(user.getId(), token);
    }

    public void validateTokenOrThrow(Long userId, String token) {
        String userIdValue = String.valueOf(userId);
        String expectedToken = redisTemplate.opsForValue().get(activeUserKey(userIdValue));
        String tokenOwner = redisTemplate.opsForValue().get(activeTokenKey(token));

        if (expectedToken == null || !expectedToken.equals(token) || tokenOwner == null || !tokenOwner.equals(userIdValue)) {
            throw new CoreException(ErrorType.UNAUTHORIZED, "유효한 입장 토큰이 필요합니다.");
        }
    }

    public void consumeToken(String loginId, String password, String token) {
        UserModel user = userService.getMyInfo(loginId, password);
        consumeToken(user.getId(), token);
    }

    public void consumeToken(Long userId, String token) {
        validateTokenOrThrow(userId, token);
        String userIdValue = String.valueOf(userId);
        redisTemplate.delete(activeUserKey(userIdValue));
        redisTemplate.delete(activeTokenKey(token));
    }

    private QueuePositionInfo waiting(Long userId) {
        String userIdValue = String.valueOf(userId);
        Long rank = redisTemplate.opsForZSet().rank(WAITING_QUEUE_KEY, userIdValue);
        if (rank == null) {
            String token = redisTemplate.opsForValue().get(activeUserKey(userIdValue));
            if (token != null) {
                return entered(userId, token);
            }
            return QueuePositionInfo.notInQueue(userId, getTotalWaitingCount());
        }

        long position = rank + 1;
        long totalWaiting = getTotalWaitingCount();
        long estimatedWaitSeconds = throughputPolicy.estimateWaitSeconds(position);
        int recommendedPollingSeconds = throughputPolicy.recommendPollingSeconds(position);
        return new QueuePositionInfo(
            userId,
            QueueStatus.WAITING,
            position,
            totalWaiting,
            estimatedWaitSeconds,
            null,
            recommendedPollingSeconds
        );
    }

    private QueuePositionInfo entered(Long userId, String token) {
        return new QueuePositionInfo(
            userId,
            QueueStatus.ENTERED,
            0L,
            getTotalWaitingCount(),
            0L,
            token,
            throughputPolicy.recommendPollingSeconds(0L)
        );
    }

    private String issueToken(String userIdValue) {
        String token = UUID.randomUUID().toString().replace("-", "");
        redisTemplate.opsForValue().set(activeUserKey(userIdValue), token, tokenTtl);
        redisTemplate.opsForValue().set(activeTokenKey(token), userIdValue, tokenTtl);
        return token;
    }

    private double nextWaitingScore() {
        Long seq = redisTemplate.opsForValue().increment(WAITING_SEQ_KEY);
        return seq != null ? seq.doubleValue() : (double) System.nanoTime();
    }

    private String activeUserKey(String userIdValue) {
        return ACTIVE_USER_PREFIX + userIdValue;
    }

    private String activeTokenKey(String token) {
        return ACTIVE_TOKEN_PREFIX + token;
    }
}
