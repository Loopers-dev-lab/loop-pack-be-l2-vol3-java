package com.loopers.domain.member;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class MemberReader {

    private final MemberRepository memberRepository;

    @Transactional(readOnly = true)
    public MemberModel getMemberByMemberId(String memberId) {
        return memberRepository.findByMemberId(new MemberId(memberId))
                .orElse(null);
    }

    @Transactional(readOnly = true)
    public MemberModel getOrThrow(String memberId) {
        return memberRepository.findByMemberId(new MemberId(memberId))
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "해당 ID의 회원이 존재하지 않습니다."));
    }

    @Transactional(readOnly = true)
    public boolean existsByMemberId(String memberId) {
        return memberRepository.existsByMemberId(new MemberId(memberId));
    }
}
