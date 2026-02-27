package com.loopers.application.user;

import com.loopers.domain.user.UserRegisterCommand;
import com.loopers.domain.user.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 사용자 도메인 Application Service.
 * 도메인 서비스를 호출하고 Model → Info 변환을 담당한다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserAppService {

    private final UserService userService;

    /**
     * 회원가입을 수행한다.
     *
     * @param command 회원가입 커맨드
     * @return 생성된 사용자 정보
     */
    @Transactional
    public UserInfo register(UserRegisterCommand command) {
        return UserInfo.from(userService.register(command));
    }

    /**
     * 인증 후 본인 정보를 조회하여 UserInfo로 반환한다.
     *
     * @param loginId 로그인 ID
     * @param loginPw 비밀번호
     * @return 사용자 정보 DTO (마스킹된 이름 포함)
     */
    public UserInfo getMyInfo(String loginId, String loginPw) {
        return UserInfo.from(userService.authenticate(loginId, loginPw));
    }

    /**
     * 인증 헤더로 인증한 뒤 비밀번호를 변경한다.
     *
     * @param loginId   인증 헤더의 로그인 ID
     * @param loginPw   인증 헤더의 비밀번호
     * @param currentPw 현재 비밀번호 (body)
     * @param newPw     새 비밀번호 (body)
     */
    @Transactional
    public void changePassword(String loginId, String loginPw,
                                String currentPw, String newPw) {
        userService.authenticateAndChangePassword(loginId, loginPw, currentPw, newPw);
    }
}
