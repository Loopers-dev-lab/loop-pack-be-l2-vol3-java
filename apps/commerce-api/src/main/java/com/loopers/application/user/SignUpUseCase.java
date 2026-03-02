package com.loopers.application.user;

import com.loopers.application.shared.annotation.UseCase;
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
     * @param loginId   로그인 ID
     * @param password  비밀번호
     * @param name      이름
     * @param birthDate 생년월일
     * @param email     이메일
     * @return 가입된 사용자 정보
     */
    public UserResult execute(String loginId, String password, String name, String birthDate, String email) {
        User user = userService.register(loginId, password, name, birthDate, email);
        return UserResult.from(user);
    }
}
