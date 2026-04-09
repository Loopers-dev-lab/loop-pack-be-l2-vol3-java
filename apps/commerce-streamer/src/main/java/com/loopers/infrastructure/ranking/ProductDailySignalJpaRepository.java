package com.loopers.infrastructure.ranking;

import com.loopers.domain.ranking.ProductDailySignalModel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface ProductDailySignalJpaRepository extends JpaRepository<ProductDailySignalModel, Long> {

    List<ProductDailySignalModel> findBySignalDate(LocalDate signalDate);

    @Modifying
    @Query(value = """
            INSERT INTO product_daily_signals (product_db_id, signal_date, view_count, like_count, order_amount, created_at, updated_at)
            VALUES (:productDbId, :signalDate, :delta, 0, 0, NOW(6), NOW(6))
            ON DUPLICATE KEY UPDATE
                view_count = view_count + :delta,
                updated_at = NOW(6)
            """, nativeQuery = true)
    void upsertViewCount(@Param("productDbId") Long productDbId,
                         @Param("signalDate") LocalDate signalDate,
                         @Param("delta") long delta);

    @Modifying
    @Query(value = """
            INSERT INTO product_daily_signals (product_db_id, signal_date, view_count, like_count, order_amount, created_at, updated_at)
            VALUES (:productDbId, :signalDate, 0, :delta, 0, NOW(6), NOW(6))
            ON DUPLICATE KEY UPDATE
                like_count = like_count + :delta,
                updated_at = NOW(6)
            """, nativeQuery = true)
    void upsertLikeCount(@Param("productDbId") Long productDbId,
                         @Param("signalDate") LocalDate signalDate,
                         @Param("delta") long delta);

    @Modifying
    @Query(value = """
            INSERT INTO product_daily_signals (product_db_id, signal_date, view_count, like_count, order_amount, created_at, updated_at)
            VALUES (:productDbId, :signalDate, 0, 0, :amount, NOW(6), NOW(6))
            ON DUPLICATE KEY UPDATE
                order_amount = order_amount + :amount,
                updated_at = NOW(6)
            """, nativeQuery = true)
    void upsertOrderAmount(@Param("productDbId") Long productDbId,
                           @Param("signalDate") LocalDate signalDate,
                           @Param("amount") double amount);
}
