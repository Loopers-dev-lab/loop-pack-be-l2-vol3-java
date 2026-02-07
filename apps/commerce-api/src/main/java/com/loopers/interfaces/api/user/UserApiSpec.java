package com.loopers.interfaces.api.user;

import com.loopers.domain.user.User;
import com.loopers.interfaces.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "User API", description = "사용자 관련 API")
public interface UserApiSpec {

    @Operation(summary = "회원가입", description = "새로운 사용자를 등록합니다.")
    ApiResponse<UserResponse.SignupResponse> createUser(UserRequest.SignupRequest request);

    @Operation(summary = "내 정보 조회", description = "인증된 사용자의 정보를 조회합니다.")
    ApiResponse<UserResponse.UserDetailResponse> getUser(User user);

    @Operation(summary = "비밀번호 변경", description = "인증된 사용자의 비밀번호를 변경합니다.")
    ApiResponse<Void> updateUserPassword(User user, UserRequest.ChangePasswordRequest request);
}
