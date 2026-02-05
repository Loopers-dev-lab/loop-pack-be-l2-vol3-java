package com.loopers.user.service;

import com.loopers.user.domain.User;
import com.loopers.user.dto.CreateUserRequest;
import com.loopers.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
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
        CreateUserRequest request = new CreateUserRequest(
                "testId", "password123!", "김준영", "1990-04-27", "test@test.com"
        );

        given(passwordEncoder.encode(request.password())).willReturn("encodedPassword");
        given(userRepository.save(any(User.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        //when
        User user = userService.createUser(request);

        //then
        assertThat(user.getLoginId()).isEqualTo(request.loginId());
        verify(userRepository).save(any(User.class));
    }

    @Test
    void 비밀번호가_암호화되어_저장된다() {
        //given
        String rawPassword = "password123!";
        String encodedPassword = "encoded_password_hash";
        CreateUserRequest request = new CreateUserRequest(
                "testId", rawPassword, "test", "1990-04-27", "test@test.com"
        );

        //password를 암호화한다.
        given(passwordEncoder.encode(rawPassword)).willReturn(encodedPassword);
        //사용자를 저장소에 저장한다.
        given(userRepository.save(any(User.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        //when
        //회원가입을 진행했을 때
        User user = userService.createUser(request);

        //then
        //비밀번호가 암호화되었는지 확인한다.
        assertThat(user.getPassword()).isEqualTo(encodedPassword);
        //암호화 로직 호출했는지 확인한다.
        verify(passwordEncoder).encode(rawPassword);
    }

}
