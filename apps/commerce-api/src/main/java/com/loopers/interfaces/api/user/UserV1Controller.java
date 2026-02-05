package com.loopers.interfaces.api.user;

import com.loopers.application.user.UserFacade;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RestController
@RequestMapping("/user")
public class UserV1Controller implements UserV1ApiSpec {

    private final UserFacade userFacade;

    @Override
    public com.loopers.interfaces.api.ApiResponse<UserV1Dto.SignupResponse> signup(UserV1Dto.SignupRequest request) {
        throw new UnsupportedOperationException("Not implemented yet");
    }
}