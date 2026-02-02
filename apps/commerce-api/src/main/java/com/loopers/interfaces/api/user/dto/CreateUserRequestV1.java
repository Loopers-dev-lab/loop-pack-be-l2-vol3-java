package com.loopers.interfaces.api.user.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;

@Getter
@Builder
public class CreateUserRequestV1 {
    private String loginId;
    private String password;
    private String name;
    private LocalDate birthDate;
    private String email;
}
