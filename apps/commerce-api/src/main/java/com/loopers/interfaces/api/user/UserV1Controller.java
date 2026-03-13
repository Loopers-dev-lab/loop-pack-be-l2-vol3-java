package com.loopers.interfaces.api.user;

import com.loopers.domain.user.UserModel;
import com.loopers.domain.user.UserRegisterCommand;
import com.loopers.domain.user.UserService;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.AuthUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * 사용자(User) 고객 API V1 REST 엔드포인트를 제공하는 컨트롤러.
 *
 * <p>회원가입, 내 정보 조회, 비밀번호 변경 기능을 제공한다.
 * {@link UserService}를 직접 호출한다.</p>
 */
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserV1Controller {

    private final UserService userService;

    /**
     * 회원가입 API. 유효한 입력으로 신규 사용자를 등록한다.
     *
     * @param request 회원가입 요청 DTO (로그인 ID, 비밀번호, 이름, 생년월일, 이메일, 주소)
     * @return 생성된 사용자 정보 응답
     */
    @PostMapping
    public ResponseEntity<ApiResponse<UserV1Dto.RegisterResponse>> register(
            @Valid @RequestBody UserV1Dto.RegisterRequest request) {
        UserModel user = userService.register(new UserRegisterCommand(
                request.getLoginId(), request.getPassword(),
                request.getUserName(), request.getBirthday(),
                request.getEmail(), request.getAddress()));
        return ResponseEntity.ok(ApiResponse.success(UserV1Dto.RegisterResponse.from(user)));
    }

    /**
     * 내 정보 조회 API. 인터셉터에서 인증된 사용자 정보를 반환한다.
     *
     * @param user 인증된 사용자 (인터셉터에서 주입)
     * @return 마스킹된 사용자 정보 응답
     */
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UserV1Dto.MyInfoResponse>> getMyInfo(@AuthUser UserModel user) {
        return ResponseEntity.ok(ApiResponse.success(UserV1Dto.MyInfoResponse.from(user)));
    }

    /**
     * 비밀번호 변경 API. 인증 후 현재 비밀번호 확인 및 새 비밀번호로 변경한다.
     *
     * @param user    인증된 사용자 (인터셉터에서 주입)
     * @param request 비밀번호 변경 요청 DTO (현재 비밀번호, 새 비밀번호)
     * @return 성공 응답
     */
    @PatchMapping("/me/password")
    public ResponseEntity<ApiResponse<Object>> changePassword(
            @AuthUser UserModel user,
            @Valid @RequestBody UserV1Dto.ChangePasswordRequest request) {
        userService.changePassword(user.getLoginId(),
                request.getCurrentPassword(), request.getNewPassword());
        return ResponseEntity.ok(ApiResponse.success());
    }
}
