package com.loopers.application.point;

import com.loopers.domain.point.PointAccount;
import com.loopers.domain.point.PointService;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 포인트 Facade
 *
 * PointService를 위임하여 포인트 관련 유스케이스를 처리한다.
 */
@Component
public class PointFacade {

    private final PointService pointService;

    public PointFacade(PointService pointService) {
        this.pointService = pointService;
    }

    /** 내 포인트 조회 */
    @Transactional(readOnly = true)
    public int getMyPoints(Long userId) {
        PointAccount account = pointService.getAccount(userId);
        return account.getBalance();
    }
}
