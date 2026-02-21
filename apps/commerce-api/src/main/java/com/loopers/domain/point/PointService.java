package com.loopers.domain.point;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.PointErrorType;
public class PointService {

    private final PointAccountRepository pointAccountRepository;

    public PointService(PointAccountRepository pointAccountRepository) {
        this.pointAccountRepository = pointAccountRepository;
    }

    public PointAccount createAccount(Long userId) {
        PointAccount account = PointAccount.create(userId);
        return pointAccountRepository.save(account);
    }

    public void use(Long userId, int amount) {
        PointAccount account = pointAccountRepository.findByUserId(userId)
                .orElseThrow(() -> new CoreException(PointErrorType.ACCOUNT_NOT_FOUND));
        account.use(amount);
    }

    public void charge(Long userId, int amount) {
        PointAccount account = pointAccountRepository.findByUserId(userId)
                .orElseThrow(() -> new CoreException(PointErrorType.ACCOUNT_NOT_FOUND));
        account.charge(amount);
    }

    public void earn(Long userId, int orderAmount) {
        PointAccount account = pointAccountRepository.findByUserId(userId)
                .orElseThrow(() -> new CoreException(PointErrorType.ACCOUNT_NOT_FOUND));

        int earnRate;
        if (orderAmount >= 100_000) {
            earnRate = 3;
        } else if (orderAmount >= 50_000) {
            earnRate = 2;
        } else {
            earnRate = 1;
        }

        int earnedPoints = orderAmount * earnRate / 100;
        account.charge(earnedPoints);
    }
}
