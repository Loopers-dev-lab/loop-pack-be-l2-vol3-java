package com.loopers.domain.point;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.PointErrorType;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class PointService {

    private final PointAccountRepository pointAccountRepository;

    public PointService(PointAccountRepository pointAccountRepository) {
        this.pointAccountRepository = pointAccountRepository;
    }

    @Transactional(timeout = 30)
    public PointAccount createAccount(Long userId) {
        PointAccount account = PointAccount.open(userId);
        return pointAccountRepository.save(account);
    }

    @Transactional(readOnly = true)
    public PointAccount getAccount(Long userId) {
        return pointAccountRepository.findByUserId(userId)
                .orElseThrow(() -> new CoreException(PointErrorType.ACCOUNT_NOT_FOUND));
    }

    /**
     * 포인트 사용 (POJO 검증 + 원자적 UPDATE)
     *
     * POJO로 금액/잔액을 빠르게 검증한 후,
     * SQL WHERE balance >= amount 조건으로 동시성을 보호한다.
     */
    @Transactional(timeout = 30)
    public void use(Long userId, int amount) {
        PointAccount account = getAccount(userId);
        account.validateUse(amount);

        int affected = pointAccountRepository.useAtomically(userId, amount);
        if (affected == 0) {
            throw new CoreException(PointErrorType.INSUFFICIENT_BALANCE);
        }
    }

    @Transactional(timeout = 30)
    public void charge(Long userId, int amount) {
        PointAccount account = getAccount(userId);
        account.validateCharge(amount);

        int affected = pointAccountRepository.chargeAtomically(userId, amount);
        if (affected == 0) {
            throw new CoreException(PointErrorType.ACCOUNT_NOT_FOUND);
        }
    }

    @Transactional(timeout = 30)
    public void earn(Long userId, int orderAmount) {
        int earnedPoints = PointAccount.calculateEarnedPoints(orderAmount);
        if (earnedPoints <= 0) {
            return;
        }

        int affected = pointAccountRepository.earnAtomically(userId, earnedPoints);
        if (affected == 0) {
            throw new CoreException(PointErrorType.ACCOUNT_NOT_FOUND);
        }
    }
}
