package com.loopers.infrastructure.member;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface MemberJpaRepository extends JpaRepository<MemberEntity, UUID> {

    Optional<MemberEntity> findByMemberId(String memberId);

    boolean existsByMemberId(String memberId);
}
