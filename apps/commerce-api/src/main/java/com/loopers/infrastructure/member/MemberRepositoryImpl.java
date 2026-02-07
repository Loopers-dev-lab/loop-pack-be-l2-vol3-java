package com.loopers.infrastructure.member;

import com.loopers.domain.member.MemberId;
import com.loopers.domain.member.MemberModel;
import com.loopers.domain.member.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

@RequiredArgsConstructor
@Component
public class MemberRepositoryImpl implements MemberRepository {
    private final MemberJpaRepository memberJpaRepository;

    @Override
    public MemberModel save(MemberModel member) {
        return memberJpaRepository.save(member);
    }

    @Override
    public boolean existsByMemberId(MemberId memberId) {
        return memberJpaRepository.existsByMemberId(memberId);
    }

    @Override
    public Optional<MemberModel> findByMemberId(MemberId memberId) {
        return memberJpaRepository.findByMemberId(memberId);
    }
}
