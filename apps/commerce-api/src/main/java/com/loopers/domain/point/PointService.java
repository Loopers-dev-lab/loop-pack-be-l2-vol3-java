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
        PointAccount account = PointAccount.create(userId);
        return pointAccountRepository.save(account);
    }

    @Transactional(readOnly = true)
    public PointAccount getAccount(Long userId) {
        return pointAccountRepository.findByUserId(userId)
                .orElseThrow(() -> new CoreException(PointErrorType.ACCOUNT_NOT_FOUND));
    }

    @Transactional(timeout = 30)
    public void use(Long userId, int amount) {
        PointAccount account = getAccountForUpdate(userId);
        account.use(amount);
        pointAccountRepository.save(account);
    }

    @Transactional(timeout = 30)
    public void charge(Long userId, int amount) {
        PointAccount account = getAccountForUpdate(userId);
        account.charge(amount);
        pointAccountRepository.save(account);
    }

    @Transactional(timeout = 30)
    public void earn(Long userId, int orderAmount) {
        PointAccount account = getAccountForUpdate(userId);
        account.earn(orderAmount);
        pointAccountRepository.save(account);
    }

    private PointAccount getAccountForUpdate(Long userId) {
        return pointAccountRepository.findByUserIdForUpdate(userId)
                .orElseThrow(() -> new CoreException(PointErrorType.ACCOUNT_NOT_FOUND));
    }
}
