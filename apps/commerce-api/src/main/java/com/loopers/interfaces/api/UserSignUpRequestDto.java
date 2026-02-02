package com.loopers.interfaces.api;

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
    private String email;
}
