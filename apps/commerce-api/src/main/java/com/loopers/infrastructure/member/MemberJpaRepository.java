package com.loopers.infrastructure.member;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MemberJpaRepository extends JpaRepository<MemberJpaEntity, Long> {
    Optional<MemberJpaEntity> findByMemberId(String memberId);
    boolean existsByMemberId(String memberId);
}
