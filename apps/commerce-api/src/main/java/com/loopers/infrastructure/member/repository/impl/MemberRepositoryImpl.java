package com.loopers.infrastructure.member.repository.impl;

import com.loopers.domain.member.MemberModel;
import com.loopers.domain.member.MemberRepository;
import com.loopers.infrastructure.member.entity.MemberEntity;
import com.loopers.infrastructure.member.repository.MemberJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

@RequiredArgsConstructor
@Component
public class MemberRepositoryImpl implements MemberRepository {

    private final MemberJpaRepository memberJpaRepository;

    @Override
    public MemberModel save(MemberModel member) {
        MemberEntity entity = MemberEntity.toEntity(member);
        return memberJpaRepository.save(entity).toModel();
    }

    @Override
    public void updatePassword(String loginId, String encodedPassword) {
        MemberEntity entity = memberJpaRepository.findByLoginId(loginId).orElseThrow();
        entity.changePassword(encodedPassword);
    }

    @Override
    public Optional<MemberModel> findByLoginId(String loginId) {
        return memberJpaRepository.findByLoginId(loginId)
            .map(MemberEntity::toModel);
    }

    @Override
    public Optional<MemberModel> findByEmail(String email) {
        return memberJpaRepository.findByEmail(email)
            .map(MemberEntity::toModel);
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
