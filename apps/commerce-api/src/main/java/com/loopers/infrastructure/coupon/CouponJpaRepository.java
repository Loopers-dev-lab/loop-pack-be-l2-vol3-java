package com.loopers.infrastructure.coupon;

import com.loopers.domain.coupon.Coupon;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface CouponJpaRepository extends JpaRepository<Coupon, Long> {

    // Command

    @Modifying
    @Query("UPDATE Coupon c SET c.issuedCount = c.issuedCount + 1 " +
            "WHERE c.id = :id AND c.issuedCount < c.maxIssueCount " +
            "AND c.expiredAt > CURRENT_TIMESTAMP AND c.deletedAt IS NULL")
    int issue(@Param("id") Long id);

    // Query

    @Query("SELECT c FROM Coupon c WHERE c.id = :id AND c.deletedAt IS NULL")
    Optional<Coupon> findActiveById(Long id);

    @Query(value = "SELECT c FROM Coupon c WHERE c.deletedAt IS NULL ORDER BY c.createdAt DESC",
           countQuery = "SELECT COUNT(c) FROM Coupon c WHERE c.deletedAt IS NULL")
    Page<Coupon> findAllActive(Pageable pageable);
}
