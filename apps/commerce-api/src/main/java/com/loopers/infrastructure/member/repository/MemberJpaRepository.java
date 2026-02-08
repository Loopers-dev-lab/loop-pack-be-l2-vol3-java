package com.loopers.infrastructure.member.repository;

import com.loopers.infrastructure.member.entity.MemberEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MemberJpaRepository extends JpaRepository<MemberEntity, Long> {

    Optional<MemberEntity> findByLoginId(String loginId);

    Optional<MemberEntity> findByEmail(String email);

    boolean existsByLoginId(String loginId);

    boolean existsByEmail(String email);
}
