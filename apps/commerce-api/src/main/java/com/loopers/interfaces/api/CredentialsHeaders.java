package com.loopers.interfaces.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 로그인 ID/비밀번호를 담는 헤더 값 DTO.
 * Argument Resolver를 통해 요청 헤더에서 바인딩·검증 후 주입된다.
 */
@Getter
@AllArgsConstructor
public class CredentialsHeaders {

    @NotBlank(message = "로그인 ID는 필수입니다.")
    @Pattern(regexp = "^[A-Za-z0-9]+$", message = "로그인 ID는 영문 대소문자, 숫자만 사용 가능합니다.")
    private String loginId;

    @NotBlank(message = "비밀번호는 필수입니다.")
    @Size(min = 8, max = 16, message = "8~16자로 입력해주세요.")
    @Pattern(
        regexp = "^[A-Za-z0-9\\p{P}\\p{S}]+$",
        message = "영문 대소문자, 숫자, 특수문자만 사용 가능합니다."
    )
    private String loginPw;
}
