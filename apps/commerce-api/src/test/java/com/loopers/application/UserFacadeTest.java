package com.loopers.application;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.loopers.application.user.SignUpCommand;
import com.loopers.application.user.UserFacade;
import com.loopers.domain.user.UserModel;
import com.loopers.domain.user.UserService;
import com.loopers.support.error.CoreException;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class UserFacadeTest {

    @InjectMocks
    private UserFacade userFacade;

    @Mock
    private UserService userService;

    @Test
    @DisplayName("회원가입 성공 테스트")
    void success_signup() {

        String rawPw = "securePassword!@";
        LocalDate birthDate = LocalDate.of(1995, 1, 1);
        SignUpCommand signUpCommand = new SignUpCommand(
            "user123",
            rawPw,
            birthDate,
            "kim",
            "yk@naver.com"
        );

        UserModel savedUser = UserModel.create("user123", "encoded_hash", birthDate, "kim", "yk@naver.com");
        given(userService.createUser(eq("user123"), eq(rawPw), eq(birthDate), eq("kim"), eq("yk@naver.com")))
            .willReturn(savedUser);

        userFacade.signUp(signUpCommand);

        verify(userService, times(1)).createUser(eq("user123"), eq(rawPw), eq(birthDate), eq("kim"), eq("yk@naver.com"));
    }

    @Test
    @DisplayName("회원가입 실패 - 이미 가입되어 있으면 예외 발생")
    void fail_already_signUp() {

        String rawPw = "securePassword!@";
        SignUpCommand signUpCommand = new SignUpCommand(
            "user123",
            rawPw,
            LocalDate.of(1995, 1, 1),
            "kim",
            "yk@naver.com"
        );

        given(userService.existsByEmail("yk@naver.com")).willReturn(true);

        assertThatThrownBy(() -> userFacade.signUp(signUpCommand))
            .isInstanceOf(CoreException.class)
            .hasMessageContaining("이미 가입되어 있는 아이디 입니다.");
    }
}
