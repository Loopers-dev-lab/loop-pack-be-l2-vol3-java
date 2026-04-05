package com.loopers.domain.queue;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class QueueService {

    private static final long TOKEN_TTL_SECONDS = 300; // 5분
    private static final int THROUGHPUT_PER_SECOND = 70; // 초당 처리량

    private final QueueRepository queueRepository;
    private final TokenRepository tokenRepository;

    public QueueEntryResult enterQueue(Long userId) {
        double score = System.currentTimeMillis();
        boolean entered = queueRepository.enter(userId, score);

        Long position = queueRepository.getPosition(userId);
        long totalWaiting = queueRepository.getTotalSize();

        return new QueueEntryResult(
                userId,
                position != null ? position + 1 : 0,
                totalWaiting,
                entered
        );
    }

    public QueuePositionResult getPosition(Long userId) {
        Long rank = queueRepository.getPosition(userId);

        if (rank != null) {
            long position = rank + 1;
            long totalWaiting = queueRepository.getTotalSize();
            long estimatedWaitSeconds = Math.max(1, (long) Math.ceil((double) position / THROUGHPUT_PER_SECOND));
            int pollingIntervalSeconds = calculatePollingInterval(position);

            return new QueuePositionResult(position, totalWaiting, estimatedWaitSeconds, pollingIntervalSeconds, null);
        }

        // 대기열에 없음 → 토큰이 발급되었는지 확인
        String token = tokenRepository.getToken(userId);
        if (token != null) {
            return new QueuePositionResult(0, 0, 0, 0, token);
        }

        return null;
    }

    public List<Long> processBatch(int batchSize) {
        Set<String> userIds = queueRepository.pollBatch(batchSize);
        List<Long> processedUserIds = new ArrayList<>();

        for (String userIdStr : userIds) {
            Long userId = Long.valueOf(userIdStr);
            String token = UUID.randomUUID().toString();
            tokenRepository.saveToken(userId, token, TOKEN_TTL_SECONDS);
            processedUserIds.add(userId);
        }

        return processedUserIds;
    }

    public void validateToken(Long userId, String token) {
        String storedToken = tokenRepository.getToken(userId);
        if (storedToken == null || !storedToken.equals(token)) {
            throw new CoreException(ErrorType.FORBIDDEN, "입장 토큰이 없거나 만료되었습니다.");
        }
    }

    public void consumeToken(Long userId) {
        tokenRepository.deleteToken(userId);
    }

    private int calculatePollingInterval(long position) {
        if (position <= 100) return 1;
        if (position <= 1000) return 4;
        return 10;
    }
}
