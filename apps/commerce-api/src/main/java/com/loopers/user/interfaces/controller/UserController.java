package com.loopers.user.interfaces.controller;

import com.loopers.user.application.facade.UserCommandFacade;
import com.loopers.user.application.facade.UserQueryFacade;
import com.loopers.user.domain.model.User;
import com.loopers.user.interfaces.controller.request.UserSignUpRequest;
import com.loopers.user.interfaces.controller.response.UserMeResponse;
import com.loopers.user.interfaces.controller.response.UserSignUpResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
public class UserController {

	private final UserCommandFacade userCommandFacade;
	private final UserQueryFacade userQueryFacade;

	public UserController(UserCommandFacade userCommandFacade, UserQueryFacade userQueryFacade) {
		this.userCommandFacade = userCommandFacade;
		this.userQueryFacade = userQueryFacade;
	}

	@PostMapping
	public ResponseEntity<UserSignUpResponse> signUp(@Valid @RequestBody UserSignUpRequest request) {
		User user = userCommandFacade.signUp(request.toCommand());
		UserSignUpResponse response = UserSignUpResponse.from(user);
		return ResponseEntity.status(HttpStatus.CREATED).body(response);
	}

	@GetMapping("/me")
	public ResponseEntity<UserMeResponse> getMe(
			@RequestHeader(value = "X-Loopers-LoginId", required = false) String loginId,
			@RequestHeader(value = "X-Loopers-LoginPw", required = false) String password) {
		User user = userQueryFacade.getMe(loginId, password);
		UserMeResponse response = UserMeResponse.from(user);
		return ResponseEntity.ok(response);
	}
}
