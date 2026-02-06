package com.loopers.application;

import com.loopers.domain.model.UserId;

public interface PasswordUpdateUseCase {

    void updatePassword(UserId userId, String currentRawPassword, String newRawPassword);
}
