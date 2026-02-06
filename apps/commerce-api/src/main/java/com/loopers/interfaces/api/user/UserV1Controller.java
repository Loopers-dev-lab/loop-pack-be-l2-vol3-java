package com.loopers.interfaces.api.user;

import com.loopers.application.user.PointsInfo;
import com.loopers.application.user.UserFacade;
import com.loopers.application.user.UserInfo;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
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

    @GetMapping("/me")
    @Override
    public ApiResponse<UserV1Dto.MyInfoResponse> getMyInfo(
        @RequestHeader(value = "X-Loopers-LoginId", required = false) String loginId
    ) {
        if (loginId == null || loginId.isBlank()) {
            throw new CoreException(ErrorType.UNAUTHORIZED, "로그인이 필요합니다.");
        }

        UserInfo userInfo = userFacade.getMyInfo(loginId);
        if (userInfo == null) {
            throw new CoreException(ErrorType.NOT_FOUND, "사용자를 찾을 수 없습니다: " + loginId);
        }

        UserV1Dto.MyInfoResponse response = UserV1Dto.MyInfoResponse.from(userInfo);
        return ApiResponse.success(response);
    }

    @GetMapping("/me/points")
    @Override
    public ApiResponse<UserV1Dto.PointsResponse> getPoints(
        @RequestHeader(value = "X-USER-ID", required = false) String userId
    ) {
        if (userId == null || userId.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "X-USER-ID 헤더가 필요합니다.");
        }

        PointsInfo pointsInfo = userFacade.getPoints(userId);
        if (pointsInfo == null) {
            throw new CoreException(ErrorType.NOT_FOUND, "사용자를 찾을 수 없습니다: " + userId);
        }

        UserV1Dto.PointsResponse response = UserV1Dto.PointsResponse.from(pointsInfo);
        return ApiResponse.success(response);
    }
}
