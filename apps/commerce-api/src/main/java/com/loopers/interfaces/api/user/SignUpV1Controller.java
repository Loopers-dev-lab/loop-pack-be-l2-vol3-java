package com.loopers.interfaces.api.user;

import com.loopers.application.user.SignUpCommand;
import com.loopers.domain.user.SignUpService;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.user.dto.UserV1Dto;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/users")
public class SignUpV1Controller {

    private final SignUpService signUpService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<Void> signUp(@Valid @RequestBody UserV1Dto.CreateRequest request) {
        SignUpCommand command = SignUpCommand.from(request);
        signUpService.signUp(command);

        return ApiResponse.success(null);
    }
}
