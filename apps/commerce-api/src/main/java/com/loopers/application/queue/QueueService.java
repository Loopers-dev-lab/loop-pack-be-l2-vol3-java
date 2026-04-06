package com.loopers.application.queue;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
@EnableConfigurationProperties(QueueProperties.class)
public class QueueService {

    private final RedisTemplate<String, String> redisTemplate;
    private final TokenService tokenService;
    private final QueueProperties queueProperties;

    public QueueResult enter(Long userId) {
        Optional<String> existingToken = tokenService.getToken(userId);

        if (existingToken.isPresent()) {
            return QueueResult.tokenIssued(userId, existingToken.get());
        }

        String userIdStr = String.valueOf(userId);
        double score = System.currentTimeMillis();

        Boolean added = redisTemplate.opsForZSet().addIfAbsent(
            queueProperties.redisKey(),
            userIdStr,
            score
        );

        Long rank = redisTemplate.opsForZSet().rank(queueProperties.redisKey(), userIdStr);
        int position = rank != null ? rank.intValue() + 1 : 1;
        int estimatedWaitSeconds = calculateEstimatedWaitSeconds(position);

        if (Boolean.TRUE.equals(added)) {
            return QueueResult.waiting(userId, position, estimatedWaitSeconds);
        } else {
            return QueueResult.alreadyInQueue(userId, position, estimatedWaitSeconds);
        }
    }

    public QueuePositionResult getPosition(Long userId) {
        Optional<String> existingToken = tokenService.getToken(userId);

        if (existingToken.isPresent()) {
            Long totalWaiting = Optional.ofNullable(
                redisTemplate.opsForZSet().size(queueProperties.redisKey())
            ).orElse(0L);
            return QueuePositionResult.tokenIssued(userId, existingToken.get(), totalWaiting);
        }

        String userIdStr = String.valueOf(userId);
        Long rank = redisTemplate.opsForZSet().rank(queueProperties.redisKey(), userIdStr);

        Long totalWaiting = Optional.ofNullable(
            redisTemplate.opsForZSet().size(queueProperties.redisKey())
        ).orElse(0L);

        if (rank == null) {
            return QueuePositionResult.notInQueue(userId, totalWaiting);
        }

        int position = rank.intValue() + 1;
        int estimatedWaitSeconds = calculateEstimatedWaitSeconds(position);

        return QueuePositionResult.waiting(userId, position, estimatedWaitSeconds, totalWaiting);
    }

    public QueueLeaveResult leave(Long userId) {
        String userIdStr = String.valueOf(userId);

        // 대기열에서 제거
        Long removed = redisTemplate.opsForZSet().remove(queueProperties.redisKey(), userIdStr);

        // 토큰이 있으면 삭제
        boolean hadToken = tokenService.hasToken(userId);
        if (hadToken) {
            tokenService.deleteToken(userId);
        }

        if (removed != null && removed > 0) {
            log.info("User left queue: userId={}", userId);
            return QueueLeaveResult.left(userId);
        } else if (hadToken) {
            log.info("User with token left: userId={}", userId);
            return QueueLeaveResult.left(userId);
        } else {
            return QueueLeaveResult.notInQueue(userId);
        }
    }

    public int calculateEstimatedWaitSeconds(int position) {
        if (position <= 0) {
            return 0;
        }

        int throughput = queueProperties.throughputPerSecond();
        if (throughput <= 0) {
            return -1; // 알 수 없음
        }

        // 기본 계산: 순번 / 초당 처리량
        int baseEstimate = (int) Math.ceil((double) position / throughput);

        // 안전 마진 20% 추가 (토큰 미사용, PG 지연 등 고려)
        return (int) Math.ceil(baseEstimate * 1.2);
    }
}