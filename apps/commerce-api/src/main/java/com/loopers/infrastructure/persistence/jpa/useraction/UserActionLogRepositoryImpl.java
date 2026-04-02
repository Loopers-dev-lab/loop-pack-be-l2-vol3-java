package com.loopers.infrastructure.persistence.jpa.useraction;

import com.loopers.domain.useraction.UserActionLog;
import com.loopers.domain.useraction.UserActionLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/**
 * UserActionLogRepository 구현체.
 */
@Repository
@RequiredArgsConstructor
public class UserActionLogRepositoryImpl implements UserActionLogRepository {

    private final UserActionLogJpaRepository jpaRepository;

    @Override
    public UserActionLog save(UserActionLog userActionLog) {
        UserActionLogJpaEntity entity = UserActionLogMapper.toJpaEntity(userActionLog);
        UserActionLogJpaEntity saved = jpaRepository.save(entity);
        return UserActionLogMapper.toDomain(saved);
    }
}
