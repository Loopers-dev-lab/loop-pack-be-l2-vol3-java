package com.loopers.interfaces.api.member;

import com.loopers.application.member.MemberApplicationService;
import com.loopers.domain.member.Member;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.auth.AuthMember;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/members")
public class MemberController {

    private final MemberApplicationService memberApplicationService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<Void> register(@Valid @RequestBody MemberDto.RegisterRequest request) {
        memberApplicationService.register(request.toCommand());
        return ApiResponse.success();
    }

    @GetMapping("/duplicate")
    public ApiResponse<MemberDto.DuplicateCheckResponse> checkDuplicateLoginId(
            @RequestParam String loginId
    ) {
        boolean exists = memberApplicationService.checkDuplicateLoginId(loginId);
        if (exists) {
            return ApiResponse.success(MemberDto.DuplicateCheckResponse.unavailable(loginId));
        }
        return ApiResponse.success(MemberDto.DuplicateCheckResponse.available(loginId));
    }

    @GetMapping("/me")
    public ApiResponse<MemberDto.MemberResponse> getMe(@AuthMember Member member) {
        return ApiResponse.success(MemberDto.MemberResponse.from(member));
    }

    @PatchMapping("/me/password")
    public ApiResponse<Void> changePassword(
            @AuthMember Member member,
            @Valid @RequestBody MemberDto.ChangePasswordRequest request
    ) {
        memberApplicationService.changePassword(request.toCommand(member.id()));
        return ApiResponse.success();
    }
}
