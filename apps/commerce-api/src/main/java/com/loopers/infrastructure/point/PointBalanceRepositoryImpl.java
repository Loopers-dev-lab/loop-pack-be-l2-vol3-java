package com.loopers.infrastructure.point;

import com.loopers.domain.point.PointBalance;
import com.loopers.domain.point.PointBalanceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class PointBalanceRepositoryImpl implements PointBalanceRepository {

    private final PointBalanceJpaRepository pointBalanceJpaRepository;

    @Override
    public PointBalance save(PointBalance pointBalance) {
        if (pointBalance.id() != null) {
            Optional<PointBalanceEntity> existing = pointBalanceJpaRepository.findById(pointBalance.id())
                    .filter(entity -> entity.getDeletedAt() == null);
            if (existing.isPresent()) {
                PointBalanceEntity entity = existing.get();
                entity.updateFrom(pointBalance);
                return pointBalanceJpaRepository.save(entity).toDomain();
            }
        }

        return pointBalanceJpaRepository.save(PointBalanceEntity.from(pointBalance)).toDomain();
    }

    @Override
    public Optional<PointBalance> findByMemberId(String memberId) {
        return pointBalanceJpaRepository.findByMemberIdAndDeletedAtIsNull(memberId)
                .map(PointBalanceEntity::toDomain);
    }

    @Override
    public void initializeIfAbsent(String memberId, int initialBalance) {
        pointBalanceJpaRepository.initializeIfAbsent(memberId, initialBalance);
    }

    @Override
    public int decreaseBalanceAtomically(String memberId, int amount) {
        return pointBalanceJpaRepository.decreaseBalanceAtomically(memberId, amount);
    }

    @Override
    public int increaseBalanceAtomically(String memberId, int amount) {
        return pointBalanceJpaRepository.increaseBalanceAtomically(memberId, amount);
    }
}
