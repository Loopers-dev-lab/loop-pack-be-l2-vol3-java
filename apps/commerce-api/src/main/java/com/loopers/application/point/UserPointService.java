package com.loopers.application.point;

import com.loopers.domain.point.UserPoint;
import com.loopers.domain.point.UserPointRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 사용자 포인트 서비스.
 */
@Service
@RequiredArgsConstructor
public class UserPointService {

    private final UserPointRepository userPointRepository;

    /**
     * 포인트 조회.
     */
    @Transactional(readOnly = true)
    public UserPointResult getPoint(Long userId) {
        UserPoint point = userPointRepository.findByUserId(userId)
            .orElseGet(() -> UserPoint.create(userId, 0));
        return UserPointResult.from(point);
    }

    /**
     * 포인트 충전.
     */
    @Transactional
    public UserPointResult charge(Long userId, long amount) {
        UserPoint point = userPointRepository.findByUserIdWithLock(userId)
            .orElseGet(() -> UserPoint.create(userId, 0));

        point.charge(amount);
        UserPoint saved = userPointRepository.save(point);

        return UserPointResult.from(saved);
    }
}
