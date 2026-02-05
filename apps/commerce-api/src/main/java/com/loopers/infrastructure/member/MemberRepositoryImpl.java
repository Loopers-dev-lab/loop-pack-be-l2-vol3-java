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
}
