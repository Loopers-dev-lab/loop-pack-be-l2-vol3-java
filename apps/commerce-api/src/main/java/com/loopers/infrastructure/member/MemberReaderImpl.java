package com.loopers.infrastructure.member;

import com.loopers.domain.member.MemberReader;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
public class MemberReaderImpl implements MemberReader {
    private final MemberJpaRepository memberJpaRepository;

    @Override
    public boolean existsByLoginId(String loginId) {
        return memberJpaRepository.existsByLoginId(loginId);
    }
}
