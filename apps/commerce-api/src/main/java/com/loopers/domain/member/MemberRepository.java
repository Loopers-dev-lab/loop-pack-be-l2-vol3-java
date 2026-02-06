package com.loopers.domain.member;

import java.util.Optional;

public interface MemberRepository {

    MemberModel save(MemberModel member);

    Optional<MemberModel> findByLoginId(String loginId);

    Optional<MemberModel> findByEmail(String email);

    boolean existsByLoginId(String loginId);

    boolean existsByEmail(String email);
}
