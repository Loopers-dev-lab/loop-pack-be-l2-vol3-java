package com.loopers.interfaces.api.member;

import com.loopers.application.member.MemberFacade;
import com.loopers.application.member.MemberInfo;
import com.loopers.interfaces.api.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/members")
public class MemberV1Controller implements MemberV1ApiSpec {

	private final MemberFacade memberFacade;

	@PostMapping
	@Override
	public ApiResponse<Void> register(@RequestBody MemberV1Dto.RegisterRequest request) {
		memberFacade.register(
				request.loginId(),
				request.password(),
				request.name(),
				request.birthDate(),
				request.email()
		);
		return ApiResponse.success(null);
	}

	@GetMapping("/me")
	@Override
	public ApiResponse<MemberV1Dto.MyInfoResponse> getMyInfo(
			@RequestHeader("X-Loopers-LoginId") String loginId,
			@RequestHeader("X-Loopers-LoginPw") String loginPw
	) {
		MemberInfo info = memberFacade.getMyInfo(loginId, loginPw);
		MemberV1Dto.MyInfoResponse response = new MemberV1Dto.MyInfoResponse(
				info.loginId(),
				info.maskedName(),
				info.birthDate(),
				info.email()
		);
		return ApiResponse.success(response);
	}

	@PatchMapping("/me/password")
	@Override
	public ApiResponse<Void> changePassword(
			@RequestHeader("X-Loopers-LoginId") String loginId,
			@RequestHeader("X-Loopers-LoginPw") String loginPw,
			@RequestBody MemberV1Dto.ChangePasswordRequest request
	) {
		memberFacade.changePassword(loginId, loginPw, request.newPassword());
		return ApiResponse.success(null);
	}
}
