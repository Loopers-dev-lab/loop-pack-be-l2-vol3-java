package com.loopers.application;

import com.loopers.domain.model.UserId;

public interface AuthenticationUseCase {

    void authenticate(UserId userId, String rawPassword);
}