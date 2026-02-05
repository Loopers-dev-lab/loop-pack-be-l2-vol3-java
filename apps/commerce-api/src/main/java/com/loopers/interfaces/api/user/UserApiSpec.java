package com.loopers.interfaces.api.user;

import com.loopers.interfaces.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "User API", description = "회원 서비스 API")
public interface UserApiSpec {

    @Operation(
        summary = "회원 가입",
        description = "로그인ID, 비밀번호, 이름, 생년월일, 이메일을 입력해서 회원가입을 합니다."
    )
    ApiResponse<UserDto.SignupResponse> signup(
        @Schema(name = "회원가입 요청", description = "회원가입 시 필요한 정보")
        UserDto.SignupRequest signRequest
    );

}
