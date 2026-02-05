package com.loopers.infrastructure.member;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/*
    Repository
    : JPA Repository - MemberEntity 전용
    
 */
public interface MemberJpaRepository extends JpaRepository<MemberEntity, Long> {

    Optional<MemberEntity> findByLoginId(String loginId);

    boolean existsByLoginId(String loginId);

    boolean existsByEmail(String email);
}
