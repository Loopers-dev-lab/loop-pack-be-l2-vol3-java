package com.loopers.application.service;

import com.loopers.application.service.dto.MemberRegisterRequest;
import com.loopers.domain.member.MemberExceptionMessage;
import com.loopers.infrastructure.member.MemberRepository;
import com.loopers.support.error.CoreException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MemberService {

    private final MemberRepository memberRepository;

    public void register(MemberRegisterRequest request) {
        boolean isLoginIdAlreadyExists = memberRepository.existsByLoginId(request.loginId());

        if (isLoginIdAlreadyExists) {
            throw new IllegalArgumentException(MemberExceptionMessage.LoginId.DUPLICATE_ID_EXISTS.message());
        }

    }
}
