package com.loopers.interfaces.api.user;

import com.loopers.application.user.UserApplicationService;
import com.loopers.application.user.UserDto;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * User V1 API Controller
 *
 * 역할:
 * - HTTP 요청/응답 처리
 * - Application DTO ↔ API DTO 변환
 * - 도메인 예외 → CoreException 변환 (Infrastructure 예외로 변환)
 *
 * 계층 분리:
 * - Application Service에 의존 (Application Layer)
 * - Application DTO (UserDto.UserInfo) 수신
 * - API DTO (UserV1Dto) 반환
 * - 도메인 객체(User)에 직접 의존하지 않음
 */
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/users")
public class UserV1Controller {

    private final UserApplicationService userApplicationService;

    @PostMapping({"", "/register"})
    public ApiResponse<UserV1Dto.CreateResponse> create(
        @RequestHeader("X-Loopers-LoginId") String loginId,
        @RequestHeader("X-Loopers-LoginPw") String password,
        @Valid @RequestBody UserV1Dto.CreateRequest request
    ) {
        try {
            // Application Service 호출 → Application DTO 수신
            UserDto.UserInfo userInfo = userApplicationService.register(
                loginId,
                password,
                request.name(),
                request.birthDate(),
                request.email(),
                request.gender()
            );

            // Application DTO → API DTO 변환
            UserV1Dto.CreateResponse response = UserV1Dto.CreateResponse.from(userInfo);
            return ApiResponse.success(response);
        } catch (IllegalArgumentException e) {
            throw new CoreException(ErrorType.BAD_REQUEST, e.getMessage());
        }
    }

    @GetMapping("/{id}")
    public ApiResponse<UserV1Dto.UserInfoResponse> getUserInfo(
        @PathVariable Long id
    ) {
        // Application Service 호출 → Application DTO 수신
        UserDto.UserInfo userInfo = userApplicationService.getUserInfo(id)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "사용자를 찾을 수 없습니다"));

        // Application DTO → API DTO 변환
        UserV1Dto.UserInfoResponse response = UserV1Dto.UserInfoResponse.from(userInfo);
        return ApiResponse.success(response);
    }

    @PatchMapping("/{id}/password")
    public ApiResponse<Void> changePassword(
        @PathVariable Long id,
        @Valid @RequestBody UserV1Dto.ChangePasswordRequest request
    ) {
        try {
            userApplicationService.changePassword(id, request.oldPassword(), request.newPassword());
            return ApiResponse.success(null);
        } catch (IllegalArgumentException e) {
            throw new CoreException(ErrorType.BAD_REQUEST, e.getMessage());
        }
    }
}
