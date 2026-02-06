package com.loopers.infrastructure.member;

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
    public Optional<MemberModel> findByLoginId(String loginId) {
        return memberJpaRepository.findByLoginId(loginId);
    }

    @Override
    public Optional<MemberModel> findByEmail(String email) {
        return memberJpaRepository.findByEmail(email);
    }

    @Override
    public boolean existsByLoginId(String loginId) {
        return memberJpaRepository.existsByLoginId(loginId);
    }

    @Override
    public boolean existsByEmail(String email) {
        return memberJpaRepository.existsByEmail(email);
    }
}
