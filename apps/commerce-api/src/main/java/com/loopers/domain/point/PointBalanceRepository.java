package com.loopers.domain.point;

import java.util.Optional;

public interface PointBalanceRepository {
    PointBalance save(PointBalance pointBalance);

    Optional<PointBalance> findByMemberId(String memberId);

    void initializeIfAbsent(String memberId, int initialBalance);

    int decreaseBalanceAtomically(String memberId, int amount);

    int increaseBalanceAtomically(String memberId, int amount);
}
