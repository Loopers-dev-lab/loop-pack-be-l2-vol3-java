package com.loopers.interfaces.api.member;

import com.loopers.application.member.MemberFacade;
import com.loopers.application.member.MemberInfo;
import com.loopers.domain.member.Member;
import com.loopers.interfaces.api.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/members")
public class MemberV1Controller {

    private final MemberFacade memberFacade;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<MemberV1Dto.MemberResponse> register(
        @RequestBody MemberV1Dto.RegisterRequest request
    ) {
        MemberInfo info = memberFacade.register(
            request.loginId(),
            request.password(),
            request.name(),
            request.birthDate(),
            request.email()
        );
        return ApiResponse.success(MemberV1Dto.MemberResponse.from(info));
    }

    @GetMapping("/me")
    public ApiResponse<MemberV1Dto.MemberResponse> getMe(HttpServletRequest request) {
        Member authenticatedMember = (Member) request.getAttribute("authenticatedMember");
        MemberInfo info = memberFacade.getMe(authenticatedMember);
        return ApiResponse.success(MemberV1Dto.MemberResponse.from(info));
    }
}
