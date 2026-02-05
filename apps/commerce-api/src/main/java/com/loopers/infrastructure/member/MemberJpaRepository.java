package com.loopers.infrastructure.member;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * JPA Repository - MemberEntity 전용
 * 
 * 도메인 모델(Member)이 아닌 영속성 엔티티(MemberEntity)를 사용합니다.
 */
public interface MemberJpaRepository extends JpaRepository<MemberEntity, Long> {

    Optional<MemberEntity> findByLoginId(String loginId);

    boolean existsByLoginId(String loginId);

    boolean existsByEmail(String email);
}
