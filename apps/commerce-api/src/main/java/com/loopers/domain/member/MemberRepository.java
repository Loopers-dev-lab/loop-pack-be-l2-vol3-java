package com.loopers.domain.member;

import java.util.Optional;

public interface MemberRepository {
    Optional<MemberModel> findByLoginId(String loginId);
    MemberModel save(MemberModel member);
    boolean existsByLoginId(String loginId);
}
