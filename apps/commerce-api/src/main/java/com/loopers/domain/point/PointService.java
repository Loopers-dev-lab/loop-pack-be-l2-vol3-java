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

    @Transactional
    public PointAccount createAccount(Long userId) {
        PointAccount account = PointAccount.create(userId);
        return pointAccountRepository.save(account);
    }

    @Transactional(readOnly = true)
    public PointAccount getAccount(Long userId) {
        return pointAccountRepository.findByUserId(userId)
                .orElseThrow(() -> new CoreException(PointErrorType.ACCOUNT_NOT_FOUND));
    }

    @Transactional
    public void use(Long userId, int amount) {
        PointAccount account = getAccount(userId);
        account.use(amount);
        pointAccountRepository.save(account);
    }

    @Transactional
    public void charge(Long userId, int amount) {
        PointAccount account = getAccount(userId);
        account.charge(amount);
        pointAccountRepository.save(account);
    }

    @Transactional
    public void earn(Long userId, int orderAmount) {
        PointAccount account = getAccount(userId);

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
        pointAccountRepository.save(account);
    }
}
