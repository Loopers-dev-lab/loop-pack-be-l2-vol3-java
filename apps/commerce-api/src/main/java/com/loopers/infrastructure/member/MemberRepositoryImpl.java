package com.loopers.infrastructure.member;

import com.loopers.domain.member.MemberModel;
import com.loopers.domain.member.MemberRepository;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
public class MemberRepositoryImpl implements MemberRepository {

  private final MemberJpaRepository memberJpaRepository;

  @Override
  public MemberModel save(MemberModel memberModel) {
    return memberJpaRepository.save(memberModel);
  }

  @Override
  public Optional<MemberModel> update(MemberModel memberModel) {
    return Optional.empty();
  }

  @Override
  public Optional<MemberModel> findByLoginId(String id) {
    return memberJpaRepository.findByLoginId(id);
  }
}
