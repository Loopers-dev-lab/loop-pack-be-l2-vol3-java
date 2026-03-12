package com.loopers.domain.point;

import java.util.Optional;

/**
 * UserPoint Repository 인터페이스.
 */
public interface UserPointRepository {

    UserPoint save(UserPoint point);

    Optional<UserPoint> findByUserId(Long userId);

    Optional<UserPoint> findByUserIdWithLock(Long userId);
}
