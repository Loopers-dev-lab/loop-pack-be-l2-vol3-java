package com.loopers.application;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.loopers.interfaces.api.UsersSignUpRequestDto;
import com.loopers.support.error.CoreException;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
public class UserServiceTest {

    @InjectMocks
    private UserService userService;

    @Mock
    private PasswordEncoder passwordEncoder;

    private UsersSignUpRequestDto createDto(LocalDate birthDate) {
        return new UsersSignUpRequestDto(birthDate, "email@test.com", "kkk@gmail.com");
    }

    @Test
    @DisplayName("회원가입 성공 테스트")
    void success_signup() {

        String rawPw = "securePassword!@";
        String encodedPw = "encoded_hash";
        UsersSignUpRequestDto dto = createDto(LocalDate.of(1995, 1, 1));

        given(passwordEncoder.encode(rawPw)).willReturn(encodedPw);

        userService.signup("user123", rawPw, dto);

        verify(passwordEncoder, times(1)).encode(rawPw);
    }

    @Test
    @DisplayName("회원가입 실패 - 비밀번호에 생년월일 포함되어있으면 예외 발생")
    void fail_with_birthDate() {

        String rawPw = "pw19911203!!"; // 생일 포함
        UsersSignUpRequestDto dto = createDto(LocalDate.of(1991,12, 3));

        assertThatThrownBy(() -> userService.signup("user123", rawPw, dto))
            .isInstanceOf(CoreException.class)
            .hasMessageContaining("생년월일을 포함할 수 없습니다");
    }
}
