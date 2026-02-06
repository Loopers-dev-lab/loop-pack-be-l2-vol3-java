package com.loopers.infrastructure.repository;

import com.loopers.infrastructure.entity.UserJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserJpaRepository extends JpaRepository<UserJpaEntity, Long> {
    Optional<UserJpaEntity> findByUserId(String userId);
    boolean existsByUserId(String userId);
}
