package com.loopers.interfaces.api.member;

import com.loopers.application.member.MemberFacade;
import com.loopers.application.member.dto.PutMemberPasswordReqDto;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.member.dto.AddMemberApiReqDto;
import com.loopers.interfaces.api.member.dto.FindMemberApiResDto;
import com.loopers.interfaces.api.member.dto.PutMemberPasswordApiReqDto;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/member")
public class MemberV1Controller implements MemberV1ApiSpec {

    private static final String HEADER_LOGIN_ID = "X-Loopers-LoginId";
    private static final String HEADER_LOGIN_PW = "X-Loopers-LoginPw";

    private final MemberFacade memberFacade;

    @PostMapping
    @Override
    public ApiResponse<Void> addMember(@RequestBody AddMemberApiReqDto request) {
        memberFacade.addMember(request.toCommand());
        return ApiResponse.successNoContent();
    }

    @GetMapping
    @Override
    public ApiResponse<FindMemberApiResDto> findMember(@RequestHeader(HEADER_LOGIN_ID) String loginId,
                                                       @RequestHeader(HEADER_LOGIN_PW) String password) {
        return ApiResponse.success(memberFacade.findMember(loginId, password));
    }

    @PutMapping("/password")
    @Override
    public ApiResponse<Void> putPassword(@RequestHeader(HEADER_LOGIN_ID) String loginId,
                                         @RequestHeader(HEADER_LOGIN_PW) String password,
                                         @RequestBody PutMemberPasswordApiReqDto request) {
        memberFacade.putPassword(request.toCommand(loginId, password));
        return ApiResponse.successNoContent();
    }
}
