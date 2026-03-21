package com.loopers.infrastructure.point;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface PointBalanceJpaRepository extends JpaRepository<PointBalanceEntity, UUID> {
    Optional<PointBalanceEntity> findByMemberIdAndDeletedAtIsNull(String memberId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            INSERT INTO point_balances (id, member_id, balance, created_at, updated_at, deleted_at)
            VALUES (UUID_TO_BIN(UUID()), :memberId, :initialBalance, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6), NULL)
            ON DUPLICATE KEY UPDATE member_id = member_id
            """, nativeQuery = true)
    int initializeIfAbsent(@Param("memberId") String memberId, @Param("initialBalance") int initialBalance);

    @Modifying
    @Query("UPDATE PointBalanceEntity p SET p.balance = p.balance - :amount WHERE p.memberId = :memberId AND p.deletedAt IS NULL AND p.balance >= :amount")
    int decreaseBalanceAtomically(@Param("memberId") String memberId, @Param("amount") int amount);

    @Modifying
    @Query("UPDATE PointBalanceEntity p SET p.balance = p.balance + :amount WHERE p.memberId = :memberId AND p.deletedAt IS NULL")
    int increaseBalanceAtomically(@Param("memberId") String memberId, @Param("amount") int amount);
}
