package com.loopers.infrastructure.persistence.jpa.useraction;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 사용자 행동 로그 JPA Repository.
 */
public interface UserActionLogJpaRepository extends JpaRepository<UserActionLogJpaEntity, Long> {
}
