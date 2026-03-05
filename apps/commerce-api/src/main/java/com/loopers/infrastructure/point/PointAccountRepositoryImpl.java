package com.loopers.infrastructure.point;

import com.loopers.domain.point.PointAccount;
import com.loopers.domain.point.PointAccountRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * 포인트 계좌 리포지토리 구현체 (Infrastructure Layer)
 *
 * Domain 계층의 PointAccountRepository 포트를 구현한다.
 * Mapper를 활용하여 Domain ↔ Entity 변환한다.
 */
@Repository
public class PointAccountRepositoryImpl implements PointAccountRepository {

    private final PointAccountJpaRepository pointAccountJpaRepository;
    private final PointAccountMapper pointAccountMapper;

    public PointAccountRepositoryImpl(PointAccountJpaRepository pointAccountJpaRepository, PointAccountMapper pointAccountMapper) {
        this.pointAccountJpaRepository = pointAccountJpaRepository;
        this.pointAccountMapper = pointAccountMapper;
    }

    @Override
    public PointAccount save(PointAccount pointAccount) {
        // Domain → Entity
        PointAccountEntity entity = pointAccountMapper.toEntity(pointAccount);

        // JPA save
        PointAccountEntity saved = pointAccountJpaRepository.save(entity);

        // Entity → Domain
        return pointAccountMapper.toDomain(saved);
    }

    @Override
    public Optional<PointAccount> findByUserId(Long userId) {
        return pointAccountJpaRepository.findByUserId(userId)
            .map(pointAccountMapper::toDomain);
    }

    @Override
    public int useAtomically(Long userId, int amount) {
        return pointAccountJpaRepository.useAtomically(userId, amount);
    }

    @Override
    public int chargeAtomically(Long userId, int amount) {
        return pointAccountJpaRepository.chargeAtomically(userId, amount);
    }

    @Override
    public int earnAtomically(Long userId, int amount) {
        return pointAccountJpaRepository.earnAtomically(userId, amount);
    }
}
