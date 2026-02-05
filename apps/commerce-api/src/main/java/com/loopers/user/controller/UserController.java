package com.loopers.user.controller;

import com.loopers.user.domain.User;
import com.loopers.user.dto.SignUpRequest;
import com.loopers.user.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RestController
public class UserController {

    private final UserService userService;

    @PostMapping("/api/v1/user/signup")
    public ResponseEntity<?> signUp(@Valid @RequestBody SignUpRequest request) {
        User user = userService.signUp(
                request.loginId(),
                request.password(),
                request.name(),
                request.birthDate(),
                request.email()
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(user);
    }
}
