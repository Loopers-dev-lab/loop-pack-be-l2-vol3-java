package com.loopers.interfaces.api.user.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;

@Getter
@Builder
public class CreateUserRequestV1 {
    @NotNull(message = "로그인 ID는 필수값입니다.")
    private String loginId;

    @NotNull(message = "비밀번호는 필수값입니다.")
    private String password;

    @NotNull(message = "이름은 필수값입니다.")
    private String name;

    @NotNull(message = "생년월일은 필수값입니다.")
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate birthDate;

    @NotNull(message = "이메일은 필수값입니다.")
    private String email;
}
