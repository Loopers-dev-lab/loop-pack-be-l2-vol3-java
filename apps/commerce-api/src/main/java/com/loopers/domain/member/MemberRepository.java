package com.loopers.domain.member;

import java.util.Optional;

public interface MemberRepository {
    Member save(Member member);
    boolean existsByLoginId(String loginId);
    boolean existsByEmail(String email);
    Optional<Member> findByLoginId(String loginId);
    void updatePassword(String loginId, String encodedPassword);
}