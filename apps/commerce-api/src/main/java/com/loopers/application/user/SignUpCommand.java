package com.loopers.application.user;

import java.time.LocalDate;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public class SignUpCommand {

    private final String loginId;
    private final String loginPw;
    private final LocalDate birthDate;
    private final String name;
    private final String email;
}
