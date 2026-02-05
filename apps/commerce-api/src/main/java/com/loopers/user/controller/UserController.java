package com.loopers.user.controller;

import com.loopers.user.domain.User;
import com.loopers.user.dto.CreateUserRequest;
import com.loopers.user.dto.CreateUserResponse;
import com.loopers.user.dto.GetMyInfoResponse;
import com.loopers.user.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    public static final String LOGIN_ID_HEADER = "X-Loopers-LoginId";

    private final UserService userService;

    @PostMapping
    public ResponseEntity<CreateUserResponse> createUser(@Valid @RequestBody CreateUserRequest request) {
        User user = userService.createUser(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(CreateUserResponse.from(user));
    }

    @GetMapping("/me")
    public ResponseEntity<GetMyInfoResponse> getMyInfo(
            @RequestHeader(LOGIN_ID_HEADER) String loginId
    ) {
        GetMyInfoResponse response = userService.getMyInfo(loginId);
        return ResponseEntity.ok(response);
    }
}
