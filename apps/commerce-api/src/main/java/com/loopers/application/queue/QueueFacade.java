package com.loopers.application.queue;

import com.loopers.domain.user.User;
import com.loopers.domain.user.UserService;
import com.loopers.infrastructure.queue.QueueCacheService;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
public class QueueFacade {

    /**
     * 처리량 설계 기준:
     * - DB 커넥션 풀: 50
     * - 주문 1건 평균 처리 시간: 200ms
     * - 이론적 최대 TPS: 50 / 0.2 = 250 TPS
     * - 안전 마진 70%: 175 TPS
     */
    private static final double THROUGHPUT_PER_SECOND = 175.0;

    private final UserService userService;
    private final QueueCacheService queueCacheService;

    public QueueInfo enter(String loginId, String rawPassword) {
        User user = userService.authenticate(loginId, rawPassword);

        if (queueCacheService.getToken(user.getId()).isPresent()) {
            throw new CoreException(ErrorType.CONFLICT, "이미 입장 토큰이 발급된 상태입니다.");
        }

        queueCacheService.enter(user.getId());

        long rank = queueCacheService.getRank(user.getId()).orElse(1L);
        long totalCount = queueCacheService.getTotalCount();
        long estimatedWaitSeconds = (long) (rank / THROUGHPUT_PER_SECOND);

        return new QueueInfo(rank, totalCount, estimatedWaitSeconds, null);
    }

    public QueueInfo getPosition(String loginId, String rawPassword) {
        User user = userService.authenticate(loginId, rawPassword);

        String token = queueCacheService.getToken(user.getId()).orElse(null);
        if (token != null) {
            return new QueueInfo(0, queueCacheService.getTotalCount(), 0, token);
        }

        long rank = queueCacheService.getRank(user.getId())
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "대기열에 진입하지 않은 사용자입니다."));
        long totalCount = queueCacheService.getTotalCount();
        long estimatedWaitSeconds = (long) (rank / THROUGHPUT_PER_SECOND);

        return new QueueInfo(rank, totalCount, estimatedWaitSeconds, null);
    }
}
