package com.loopers.application.user.command;

import com.loopers.domain.user.vo.BirthDate;
import com.loopers.domain.user.vo.UserId;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ChangePasswordCommand {
    private final UserId userId;
    private final String newRawPassword;
    private final BirthDate birthDate;
}
