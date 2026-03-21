package com.loopers.application.point;

import com.loopers.domain.point.PointBalanceRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PointApplicationService {

    private static final int INITIAL_POINT_BALANCE = 1_000_000;

    private final PointBalanceRepository pointBalanceRepository;

    @Transactional
    public int use(String memberId, int amount) {
        if (amount < 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "사용 포인트는 0 이상이어야 합니다.");
        }
        if (amount == 0) {
            return 0;
        }

        pointBalanceRepository.initializeIfAbsent(memberId, INITIAL_POINT_BALANCE);
        int updatedCount = pointBalanceRepository.decreaseBalanceAtomically(memberId, amount);
        if (updatedCount == 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "포인트가 부족합니다.");
        }
        return amount;
    }

    @Transactional
    public void restore(String memberId, int amount) {
        if (amount < 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "복구 포인트는 0 이상이어야 합니다.");
        }
        if (amount == 0) {
            return;
        }

        pointBalanceRepository.initializeIfAbsent(memberId, INITIAL_POINT_BALANCE);
        int updatedCount = pointBalanceRepository.increaseBalanceAtomically(memberId, amount);
        if (updatedCount == 0) {
            throw new CoreException(ErrorType.NOT_FOUND, "포인트 잔액 정보를 찾을 수 없습니다.");
        }
    }
}
