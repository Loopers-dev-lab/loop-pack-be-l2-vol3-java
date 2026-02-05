package com.loopers.application.user;

import com.loopers.domain.UserModel;
import com.loopers.domain.UserService;
import com.loopers.interfaces.user.ChangePasswordRequest;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import java.time.LocalDate;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@AllArgsConstructor
public class UserFacade {

    private final UserService userService;

    public UserInfo signUp(SignUpCommand command) {
        if (userService.existsByEmail(command.getEmail())) {
            throw new CoreException(ErrorType.BAD_REQUEST, "이미 가입되어 있는 아이디 입니다.");
        }

        // TODO
        // 만약 또다른 패스워드 검증 조건이 생기거나 다른 클래스에서도 같이 사용한다면 클래스로 분리해야함
        validatePasswordContent(command.getLoginPw(), command.getBirthDate());

        UserModel userModel = userService.createUser(
            command.getLoginId(),
            command.getLoginPw(),
            command.getBirthDate(),
            command.getName(),
            command.getEmail()
        );

        return UserInfo.from(userModel);
    }

    public UserInfo getMyInfo(Long userId) {
        UserModel user = userService.findById(userId);
        return UserInfo.from(user);
    }

    private void validatePasswordContent(String password, LocalDate birthDate) {
        if (password == null || birthDate == null) {
            return;
        }

        String birthStr = birthDate.toString().replace("-", "");

        if (password.contains(birthStr)) {
            throw new CoreException(ErrorType.NOT_INCLUDE_BIRTH_IN_PASSWORD);
        }
    }

    public void changePassword(Long userId, ChangePasswordRequest request) {
        userService.changePassword(userId, request.currentPassword(), request.newPassword());
    }
}
