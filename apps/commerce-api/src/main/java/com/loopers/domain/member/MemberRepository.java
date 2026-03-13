package com.loopers.domain.member;

import com.loopers.domain.member.vo.LoginId;

import java.util.Optional;

public interface MemberRepository {
    Member save(Member member);
    Optional<Member> findByLoginId(LoginId loginId);
    boolean existsByLoginId(LoginId loginId);
}
