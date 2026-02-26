package com.loopers.application.user;

import com.loopers.domain.model.user.UserId;

import java.time.LocalDate;

public interface UserQueryUseCase {

    UserInfoResponse getUserInfo(UserId userId);

    record UserInfoResponse(
            String loginId,
            String maskedName,
            LocalDate birthday,
            String email
    ) {}
}
