package com.loopers.infrastructure.member;

import org.springframework.data.jpa.repository.JpaRepository;

public interface MemberJpaRepository extends JpaRepository<MemberEntity, Long> {
    boolean existsByLoginId(String loginId);
    boolean existsByEmail(String email);
}