package com.loopers.infrastructure.member;

import com.loopers.domain.member.MemberModel;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MemberJpaRepository extends JpaRepository<MemberModel, Long> {

  Optional<MemberModel> findByLoginId(String loginId);
  MemberModel save(MemberModel memberModel);
}
