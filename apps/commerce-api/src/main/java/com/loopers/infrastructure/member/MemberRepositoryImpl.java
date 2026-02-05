package com.loopers.infrastructure.member;

import com.loopers.domain.member.Member;
import com.loopers.domain.member.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class MemberRepositoryImpl implements MemberRepository {

    private final MemberJpaRepository memberJpaRepository;

    @Override
    public Member save(Member member) {
        return memberJpaRepository.save(member);
    }

    @Override
    public Optional<Member> findByMemberIdValue(String memberIdValue) {
        return memberJpaRepository.findByMemberIdValue(memberIdValue);
    }

    @Override
    public boolean existsByMemberIdValue(String memberIdValue) {
        return memberJpaRepository.existsByMemberIdValue(memberIdValue);
    }
}
