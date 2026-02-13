package com.loopers.interfaces.api.user;

import com.loopers.domain.user.User;
import com.loopers.interfaces.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * 사용자 API 스펙 인터페이스
 *
 * Swagger(OpenAPI) 문서화 어노테이션과 Controller 메서드 시그니처를 분리하여
 * Controller 구현체의 가독성을 높이고, API 계약을 명시적으로 정의한다.
 */
@Tag(name = "User API", description = "사용자 관련 API")
public interface UserApiSpec {

    @Operation(summary = "회원가입", description = "새로운 사용자를 등록합니다.")
    ApiResponse<UserResponse.SignupResponse> createUser(UserRequest.SignupRequest request);

    @Operation(summary = "내 정보 조회", description = "인증된 사용자의 정보를 조회합니다.")
    ApiResponse<UserResponse.UserDetailResponse> getUser(User user);

    @Operation(summary = "비밀번호 변경", description = "인증된 사용자의 비밀번호를 변경합니다.")
    ApiResponse<Void> updateUserPassword(User user, UserRequest.ChangePasswordRequest request);
}
