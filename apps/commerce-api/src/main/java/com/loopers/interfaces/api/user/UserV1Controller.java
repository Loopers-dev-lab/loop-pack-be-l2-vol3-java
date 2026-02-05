package com.loopers.interfaces.api.user;

import com.loopers.application.user.UserFacade;
import com.loopers.application.user.UserInfo;
import com.loopers.interfaces.api.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/users")
public class UserV1Controller implements UserV1ApiSpec {

    private final UserFacade userFacade;

    @GetMapping("/me")
    @Override
    public ApiResponse<UserV1Dto.UserResponse> getMyInfo(
            @RequestHeader("X-Loopers-LoginId") String loginId,
            @RequestHeader("X-Loopers-LoginPw") String loginPw
    ) {
        UserInfo info = userFacade.getMyInfo(loginId, loginPw);
        return ApiResponse.success(UserV1Dto.UserResponse.from(info));
    }
}
