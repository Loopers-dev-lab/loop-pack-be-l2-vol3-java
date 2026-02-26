package com.loopers.application.user;

import java.time.LocalDate;

public interface RegisterUseCase {

    void register(RegisterCommand command);

    record RegisterCommand(
            String loginId, String name, String rawPassword,
            LocalDate birthday, String email
    ) {}
}
