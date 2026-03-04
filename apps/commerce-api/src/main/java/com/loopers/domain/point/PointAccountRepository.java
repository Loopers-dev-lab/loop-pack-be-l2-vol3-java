package com.loopers.domain.point;

import java.util.Optional;

public interface PointAccountRepository {
    PointAccount save(PointAccount pointAccount);
    Optional<PointAccount> findByUserId(Long userId);
    Optional<PointAccount> findByUserIdForUpdate(Long userId);
}
