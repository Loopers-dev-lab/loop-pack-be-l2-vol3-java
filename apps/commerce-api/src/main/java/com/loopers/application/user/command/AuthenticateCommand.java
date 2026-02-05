package com.loopers.application.user.command;

import com.loopers.domain.user.vo.UserId;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class AuthenticateCommand {
    private final UserId userId;
    private final String rawPassword;
}
