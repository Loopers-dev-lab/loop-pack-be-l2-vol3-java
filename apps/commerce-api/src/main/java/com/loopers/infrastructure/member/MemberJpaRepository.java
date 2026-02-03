package com.loopers.infrastructure.member;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MemberJpaRepository extends JpaRepository<MemberEntity, Long> {
    boolean existsByLoginId(String loginId);
    boolean existsByEmail(String email);
    Optional<MemberEntity> findByLoginId(String loginId);
}