package com.loopers.infrastructure.user;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface UserJpaRepository extends JpaRepository<UserEntity, Long> {

    Optional<UserEntity> findByUserId(String userId);

    boolean existsByUserId(String userId);

    @Query("SELECT u.id FROM UserEntity u WHERE u.userId = :loginId")
    Optional<Long> findIdByUserId(@Param("loginId") String loginId);
}
