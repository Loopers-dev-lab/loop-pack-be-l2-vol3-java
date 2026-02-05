package com.loopers.infrastructure.member;

import com.loopers.domain.member.Member;
import com.loopers.domain.member.MemberRepository;
import com.loopers.domain.member.vo.LoginId;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@RequiredArgsConstructor
@Repository
public class MemberRepositoryImpl implements MemberRepository {

    private final MemberJpaRepository memberJpaRepository;

    @Override
    public Member save(Member member) {
        return memberJpaRepository.save(member);
    }

    @Override
    public Optional<Member> findByLoginId(LoginId loginId) {
        return memberJpaRepository.findByLoginIdValue(loginId.value());
    }

    @Override
    public boolean existsByLoginId(LoginId loginId) {
        return memberJpaRepository.existsByLoginIdValue(loginId.value());
    }
}
