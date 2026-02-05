package com.loopers.interfaces.api.member;

import com.loopers.application.member.MemberFacade;
import com.loopers.application.member.MemberInfo;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.member.MemberV1Dto.ChangePasswordRequest;
import com.loopers.interfaces.api.member.MemberV1Dto.MemberInfoResponse;
import com.loopers.interfaces.api.member.MemberV1Dto.SignUpRequest;
import com.loopers.interfaces.api.member.MemberV1Dto.SignUpResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/members")
public class MemberV1Controller implements MemberV1ApiSpec {

  //  클라이언트 → [SignUpRequest (record)] → Controller → Facade → Service → [MemberModel (entity)] → DB
  //                   요청 데이터 전달용                                           DB에 저장되는 객체
  //  DB → [MemberModel (entity)] → Facade → [SignUpResponse (record)] → Controller → 클라이언트
  //            DB에서 꺼낸 객체                     응답 데이터 전달용
  private final MemberFacade memberFacade;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Override
    public ApiResponse<SignUpResponse> signUp(@RequestBody SignUpRequest request) {
        MemberInfo info = memberFacade.signupMember(request);
        MemberV1Dto.SignUpResponse response = MemberV1Dto.SignUpResponse.from(info);
        return ApiResponse.success(response);
    }

    @GetMapping("/{loginId}")
    @Override
    public ApiResponse<MemberInfoResponse> getMyInfo(
            @PathVariable(value = "loginId") String loginId
    ) {
        MemberInfo info = memberFacade.getMyInfo(loginId);
        MemberInfoResponse response = MemberInfoResponse.from(info);
        return ApiResponse.success(response);
    }

    @PatchMapping("/{loginId}/password")
    public ApiResponse<String> changePassword(
        @PathVariable String loginId,
        @RequestBody ChangePasswordRequest request
    ) {
      memberFacade.changePassword(loginId, request.oldPassword(), request.newPassword());
      return ApiResponse.success("비밀번호가 변경되었습니다.");
    }


}
