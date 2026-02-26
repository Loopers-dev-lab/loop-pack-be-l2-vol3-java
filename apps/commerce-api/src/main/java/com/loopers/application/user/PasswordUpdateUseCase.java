package com.loopers.application.user;

import com.loopers.domain.model.user.UserId;

public interface PasswordUpdateUseCase {

    void updatePassword(UserId userId, String currentRawPassword, String newRawPassword);
}
