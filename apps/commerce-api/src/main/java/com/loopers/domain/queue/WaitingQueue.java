package com.loopers.domain.queue;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorMessage;
import com.loopers.support.error.ErrorType;

/**
 * 대기열 도메인
 * - 유저의 대기열 진입 상태(순번, 전체 대기 인원)를 표현한다.
 * - 예상 대기 시간 계산 비즈니스 규칙을 캡슐화한다.
 */
public class WaitingQueue {

    private final Long userId;
    private final long position;
    private final long totalWaiting;

    private WaitingQueue(Long userId, long position, long totalWaiting) {
        this.userId = userId;
        this.position = position;
        this.totalWaiting = totalWaiting;
    }

    public static WaitingQueue of(Long userId, long position, long totalWaiting) {
        validate(userId);
        return new WaitingQueue(userId, position, totalWaiting);
    }

    private static void validate(Long userId) {
        if (userId == null || userId <= 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, ErrorMessage.Queue.USER_ID_INVALID);
        }
    }

    /**
     * 예상 대기 시간(초) 계산
     * - 공식: 내 순번 / 초당 처리량(TPS)
     */
    public long calculateEstimatedWaitSeconds(int tps) {
        if (tps <= 0) {
            return 0L;
        }
        return (long) Math.ceil((double) position / tps);
    }

    public Long getUserId() {
        return userId;
    }

    public long getPosition() {
        return position;
    }

    public long getTotalWaiting() {
        return totalWaiting;
    }
}
