package com.loopers.interfaces.api.user;

import com.loopers.application.user.UserFacade;
import com.loopers.application.user.UserInfo;
import com.loopers.interfaces.api.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/customer/v1/users")
public class UserV1Controller implements UserV1ApiSpec {

    private final UserFacade userFacade;

    public UserV1Controller(UserFacade userFacade) {
        this.userFacade = userFacade;
    }

    @PostMapping("/sign-up")
    @ResponseStatus(HttpStatus.CREATED)
    @Override
    public ApiResponse<UserV1Dto.SignUpResponse> signUp(
        @Valid @RequestBody UserV1Dto.SignUpRequest request
    ) {
        UserInfo userInfo = userFacade.signUp(
            request.userId(),
            request.password(),
            request.email(),
            request.birthDate(),
            request.gender()
        );
        
        UserV1Dto.SignUpResponse response = UserV1Dto.SignUpResponse.from(userInfo);
        return ApiResponse.success(response);
    }
}
