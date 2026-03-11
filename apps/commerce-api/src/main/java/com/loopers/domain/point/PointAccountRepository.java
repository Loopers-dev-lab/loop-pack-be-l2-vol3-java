package com.loopers.domain.point;

import java.util.Optional;

public interface PointAccountRepository {
    PointAccount save(PointAccount pointAccount);
    Optional<PointAccount> findByUserId(Long userId);
    int useAtomically(Long userId, int amount);
    int chargeAtomically(Long userId, int amount);
    int earnAtomically(Long userId, int amount);
}
