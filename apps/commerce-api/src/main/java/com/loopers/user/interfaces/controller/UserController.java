package com.loopers.user.interfaces.controller;


import com.loopers.user.application.dto.out.UserMeOutDto;
import com.loopers.user.application.dto.out.UserSignUpOutDto;
import com.loopers.user.application.facade.UserCommandFacade;
import com.loopers.user.application.facade.UserQueryFacade;
import com.loopers.user.interfaces.controller.request.UserChangePasswordRequest;
import com.loopers.user.interfaces.controller.request.UserSignUpRequest;
import com.loopers.user.interfaces.controller.response.UserMeResponse;
import com.loopers.user.interfaces.controller.response.UserSignUpResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;


@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

	// service
	private final UserCommandFacade userCommandFacade;
	// service
	private final UserQueryFacade userQueryFacade;


	/**
	 * 유저 컨트롤러
	 * 1. 회원가입
	 * 2. 내 정보 조회
	 * 3. 내 비밀번호 변경
	 */

	// 1. 회원가입
	@PostMapping
	public ResponseEntity<UserSignUpResponse> signUp(@Valid @RequestBody UserSignUpRequest request) {

		// 요청 객체를 회원가입 입력 DTO로 변환
		UserSignUpOutDto outDto = userCommandFacade.signUp(request.toInDto());

		// 회원가입 결과 DTO를 응답 객체로 변환
		UserSignUpResponse response = UserSignUpResponse.from(outDto);

		// 201 Created와 회원 정보 응답 반환
		return ResponseEntity.status(HttpStatus.CREATED).body(response);
	}


	// 2. 내 정보 조회
	@GetMapping("/me")
	public ResponseEntity<UserMeResponse> getMe(
		@RequestHeader(value = "X-Loopers-LoginId", required = false) String loginId,
		@RequestHeader(value = "X-Loopers-LoginPw", required = false) String password
	) {

		// 헤더 인증 정보로 내 정보 조회
		UserMeOutDto outDto = userQueryFacade.getMe(loginId, password);

		// 조회 결과 DTO를 응답 객체로 변환
		UserMeResponse response = UserMeResponse.from(outDto);

		// 200 OK와 내 정보 응답 반환
		return ResponseEntity.ok(response);
	}


	// 3. 내 비밀번호 변경
	@PatchMapping("/me/password")
	public ResponseEntity<Void> changePassword(
		@RequestHeader(value = "X-Loopers-LoginId", required = false) String loginId,
		@RequestHeader(value = "X-Loopers-LoginPw", required = false) String password,
		@Valid @RequestBody UserChangePasswordRequest request
	) {

		// 비밀번호 변경 요청 DTO 변환 후 변경 처리
		userCommandFacade.changePassword(loginId, password, request.toInDto());

		// 200 OK 반환
		return ResponseEntity.ok().build();
	}

}
