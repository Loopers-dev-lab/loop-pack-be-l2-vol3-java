package com.loopers.infrastructure.persistence.jpa.point;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

/**
 * UserPoint JPA Repository.
 */
public interface UserPointJpaRepository extends JpaRepository<UserPointJpaEntity, Long> {

    Optional<UserPointJpaEntity> findByUserId(Long userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT up FROM UserPointJpaEntity up WHERE up.userId = :userId")
    Optional<UserPointJpaEntity> findByUserIdWithLock(@Param("userId") Long userId);
}
