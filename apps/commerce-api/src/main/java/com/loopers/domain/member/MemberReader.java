package com.loopers.domain.member;

import java.util.Optional;

public interface MemberReader {
    boolean existsByLoginId(String loginId);
    Optional<Member> findByLoginId(String loginId);
}
