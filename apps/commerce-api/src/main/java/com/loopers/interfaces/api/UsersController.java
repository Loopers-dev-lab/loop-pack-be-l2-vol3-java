package com.loopers.interfaces.api;

import com.loopers.application.UserService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/users")
@AllArgsConstructor
public class UsersController {

    private final UserService userService;

    @PostMapping
    public ApiResponse<String> signUp(
        @RequestHeader(LoopersHeaders.X_LOOPERS_LOGIN_ID) @NotBlank(message = "로그인 ID는 필수입니다.") String loginId,
        @RequestHeader(LoopersHeaders.X_LOOPERS_LOGIN_PW) @NotBlank(message = "비밀번호는 필수입니다.") @Size(min = 8, max = 16, message = "8~16자로 입력해주세요.")
        @Pattern(
            regexp = "^[A-Za-z0-9\\p{P}\\p{S}]+$",
            message = "영문 대소문자, 숫자, 특수문자만 사용 가능합니다."
        )
        String loginPw,
        @Valid @RequestBody UsersSignUpRequestDto requestDto
    ) {
        userService.signup(loginId, loginPw, requestDto);
        return ApiResponse.success("ok");
    }
}
