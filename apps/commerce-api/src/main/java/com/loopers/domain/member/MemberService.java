package com.loopers.domain.member;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Component
public class MemberService {

    private final MemberRepository memberRepository;

    @Transactional
    public Member register(String loginId, String encryptedPassword, String name, String birthday, String email) {
        memberRepository.findByLoginId(loginId).ifPresent(m -> {
            throw new CoreException(ErrorType.CONFLICT, "이미 가입된 로그인 ID입니다.");
        });
        Member member = new Member(loginId, encryptedPassword, name, birthday, email);
        return memberRepository.save(member);
    }

    @Transactional(readOnly = true)
    public Member getMember(String loginId) {
        return memberRepository.findByLoginId(loginId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "회원을 찾을 수 없습니다."));
    }
}
