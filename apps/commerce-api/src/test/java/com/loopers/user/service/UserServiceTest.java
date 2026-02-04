package com.loopers.user.service;

import com.loopers.user.domain.User;
import com.loopers.user.repository.UserRepository;
import com.loopers.user.validator.PasswordValidator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
public class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private UserService userService;

    @Test
    void 정상_입력시_회원가입_성공() {
        //given
        String loginId = "testId";
        String password = "password123!";
        String name = "김준영";
        String birthDate = "19900427";
        String email = "test@test.com";

        given(userRepository.save(any(User.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        //when
        User user = userService.signUp(loginId, password, name, birthDate, email);

        //then
        assertThat(user.getLoginId()).isEqualTo(loginId);
        verify(userRepository).save(any(User.class));
    }

    @Test
    void 중복_가입된_ID_입력_시_예외_발생() {
        //given
        String loginId = "existingId";
        given(userRepository.existsByLoginId(loginId)).willReturn(true);

        //when
        Throwable thrown = catchThrowable(() -> userService.signUp(loginId, "password123!", "김준영", "19900427", "test@test.com"));

        //then
        assertThat(thrown).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 비밀번호가_암호화되어_저장된다() {
        //given
        String rawPassword = "password123!";
        String encodedPassword = "encoded_password_hash";

        //password를 암호화한다.
        given(passwordEncoder.encode(rawPassword)).willReturn(encodedPassword);
        //사용자를 저장소에 저장한다.
        given(userRepository.save(any(User.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        //when
        //회원가입을 진행했을 때
        User user = userService.signUp("testId", rawPassword, "test", "19900427", "test@test.com");

        //then
        //비밀번호가 암호화되었는지 확인한다.
        assertThat(user.getPassword()).isEqualTo(encodedPassword);
        //암호화 로직 호출했는지 확인한다.
        verify(passwordEncoder).encode(rawPassword);
    }

}
