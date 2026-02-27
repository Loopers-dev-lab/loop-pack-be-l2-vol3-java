package com.loopers.infrastructure.member.repository.impl;

import com.loopers.domain.member.model.Member;
import com.loopers.domain.member.repository.MemberRepository;
import com.loopers.domain.member.vo.Email;
import com.loopers.domain.member.vo.LoginId;
import com.loopers.domain.member.vo.Password;
import com.loopers.infrastructure.member.entity.MemberEntity;
import com.loopers.infrastructure.member.repository.MemberJpaRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

@RequiredArgsConstructor
@Component
public class MemberRepositoryImpl implements MemberRepository {

    private final MemberJpaRepository memberJpaRepository;

    @Override
    public Member save(Member member) {
        MemberEntity entity = MemberEntity.toEntity(member);
        return memberJpaRepository.save(entity).toModel();
    }

    @Override
    public void updatePassword(LoginId loginId, Password password) {
        MemberEntity entity = memberJpaRepository.findByLoginId(loginId.value()).orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "회원을 찾을 수 없습니다."));
        entity.changePassword(password.value());
    }

    @Override
    public Optional<Member> findByLoginId(LoginId loginId) {
        return memberJpaRepository.findByLoginId(loginId.value()).map(MemberEntity::toModel);
    }

    @Override
    public boolean existsByLoginId(LoginId loginId) {
        return memberJpaRepository.existsByLoginId(loginId.value());
    }

    @Override
    public boolean existsByEmail(Email email) {
        return memberJpaRepository.existsByEmail(email.value());
    }
}
