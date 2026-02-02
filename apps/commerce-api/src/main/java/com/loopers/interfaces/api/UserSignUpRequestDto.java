package com.loopers.interfaces.api;

import jakarta.validation.constraints.Email;
import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class UserSignUpRequestDto {

    private String loginId;
    private String pwd;
    private LocalDate birthDate;
    private String name;

    @Email(message = "올바른 이메일 형식이 아닙니다.")
    private String email;
}
