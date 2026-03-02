package com.loopers.domain.member.repository;

import com.loopers.domain.member.model.Member;
import com.loopers.domain.member.vo.Email;
import com.loopers.domain.member.vo.LoginId;
import com.loopers.domain.member.vo.Password;

import java.util.Optional;

public interface MemberRepository {

    Member save(Member member);

    void updatePassword(LoginId loginId, Password password);

    Optional<Member> findByLoginId(LoginId loginId);

    boolean existsByLoginId(LoginId loginId);

    boolean existsByEmail(Email email);
}
