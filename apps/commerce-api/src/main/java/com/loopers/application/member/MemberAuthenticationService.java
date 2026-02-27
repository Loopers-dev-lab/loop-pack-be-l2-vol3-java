package com.loopers.application.member;

import com.loopers.application.member.command.AuthenticateCommand;
import com.loopers.domain.member.PasswordEncoder;
import com.loopers.domain.member.Member;
import com.loopers.domain.member.MemberRepository;
import com.loopers.domain.member.vo.MemberId;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Service
public class MemberAuthenticationService {

    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional(readOnly = true)
    public Member authenticate(AuthenticateCommand command) {
        Member member = memberRepository.findByMemberId(command.memberId())
                .orElseThrow(() -> new CoreException(ErrorType.UNAUTHORIZED));

        if (!passwordEncoder.matches(command.rawPassword(), member.password().value())) {
            throw new CoreException(ErrorType.UNAUTHORIZED);
        }

        return member;
    }

    public Long findDbIdByMemberId(MemberId memberId) {
        return memberRepository.findDbIdByMemberId(memberId)
                .orElseThrow(() -> new CoreException(ErrorType.UNAUTHORIZED, "회원을 찾을 수 없습니다."));
    }

    public Long findDbIdByMember(Member member) {
        return memberRepository.findDbIdByMemberId(member.id())
                .orElseThrow(() -> new CoreException(ErrorType.UNAUTHORIZED, "회원을 찾을 수 없습니다."));
    }
}
