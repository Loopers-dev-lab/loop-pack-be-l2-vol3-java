package com.loopers.infrastructure.persistence.jpa.point;

import com.loopers.domain.point.UserPoint;
import com.loopers.domain.point.UserPointRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * UserPointRepository 구현체.
 */
@Repository
@RequiredArgsConstructor
public class UserPointRepositoryImpl implements UserPointRepository {

    private final UserPointJpaRepository jpaRepository;

    @Override
    public UserPoint save(UserPoint point) {
        UserPointJpaEntity entity;

        if (point.getId() == null) {
            entity = UserPointMapper.toJpaEntity(point);
        } else {
            entity = jpaRepository.findById(point.getId())
                .orElseGet(() -> UserPointMapper.toJpaEntity(point));
            UserPointMapper.updateJpaEntity(entity, point);
        }

        UserPointJpaEntity saved = jpaRepository.save(entity);
        return UserPointMapper.toDomain(saved);
    }

    @Override
    public Optional<UserPoint> findByUserId(Long userId) {
        return jpaRepository.findByUserId(userId)
            .map(UserPointMapper::toDomain);
    }

    @Override
    public Optional<UserPoint> findByUserIdWithLock(Long userId) {
        return jpaRepository.findByUserIdWithLock(userId)
            .map(UserPointMapper::toDomain);
    }
}
