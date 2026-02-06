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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

	private final UserCommandFacade userCommandFacade;
	private final UserQueryFacade userQueryFacade;

	@PostMapping
	public ResponseEntity<UserSignUpResponse> signUp(@Valid @RequestBody UserSignUpRequest request) {
		UserSignUpOutDto outDto = userCommandFacade.signUp(request.toInDto());
		UserSignUpResponse response = UserSignUpResponse.from(outDto);
		return ResponseEntity.status(HttpStatus.CREATED).body(response);
	}

	@GetMapping("/me")
	public ResponseEntity<UserMeResponse> getMe(
			@RequestHeader(value = "X-Loopers-LoginId", required = false) String loginId,
			@RequestHeader(value = "X-Loopers-LoginPw", required = false) String password) {
		UserMeOutDto outDto = userQueryFacade.getMe(loginId, password);
		UserMeResponse response = UserMeResponse.from(outDto);
		return ResponseEntity.ok(response);
	}

	@PatchMapping("/me/password")
	public ResponseEntity<Void> changePassword(
			@RequestHeader(value = "X-Loopers-LoginId", required = false) String loginId,
			@RequestHeader(value = "X-Loopers-LoginPw", required = false) String password,
			@Valid @RequestBody UserChangePasswordRequest request) {
		userCommandFacade.changePassword(loginId, password, request.toInDto());
		return ResponseEntity.ok().build();
	}
}
