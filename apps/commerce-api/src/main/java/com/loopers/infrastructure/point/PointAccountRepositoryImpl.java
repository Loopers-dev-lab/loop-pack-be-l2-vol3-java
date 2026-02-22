package com.loopers.infrastructure.point;

import com.loopers.domain.point.PointAccount;
import com.loopers.domain.point.PointAccountRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class PointAccountRepositoryImpl implements PointAccountRepository {

    private final PointAccountJpaRepository pointAccountJpaRepository;

    public PointAccountRepositoryImpl(PointAccountJpaRepository pointAccountJpaRepository) {
        this.pointAccountJpaRepository = pointAccountJpaRepository;
    }

    @Override
    public PointAccount save(PointAccount pointAccount) {
        return pointAccountJpaRepository.save(pointAccount);
    }

    @Override
    public Optional<PointAccount> findByUserId(Long userId) {
        return pointAccountJpaRepository.findByUserId(userId);
    }
}
