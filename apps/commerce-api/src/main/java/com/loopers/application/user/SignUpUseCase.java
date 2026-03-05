package com.loopers.application.user;

import com.loopers.application.shared.annotation.UseCase;
import com.loopers.application.user.UserCommand.SignUpCommand;
import com.loopers.domain.user.User;
import com.loopers.domain.user.UserService;

import lombok.RequiredArgsConstructor;

/**
 * 사용자가 회원가입합니다.
 *
 * <p>로그인 ID, 비밀번호, 이름, 생년월일, 이메일을 받아 신규 사용자를 등록하고 가입 결과를 반환합니다.</p>
 */
@UseCase
@RequiredArgsConstructor
public class SignUpUseCase {

    private final UserService userService;

    /**
     * @param command 회원가입 커맨드
     * @return 가입된 사용자 정보
     */
    public UserResult execute(SignUpCommand command) {
        User user = userService.register(command.toNewUser());
        return UserResult.from(user);
    }
}
