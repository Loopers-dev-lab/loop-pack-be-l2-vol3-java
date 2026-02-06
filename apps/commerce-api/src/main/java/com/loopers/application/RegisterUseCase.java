package com.loopers.application;

import java.time.LocalDate;

public interface RegisterUseCase {

    void register(String loginId, String name, String rawPassword, LocalDate birthday, String email);
}
