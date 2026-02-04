package com.loopers.user;

import com.loopers.user.domain.User;
import com.loopers.user.repository.UserRepository;
import com.loopers.user.service.UserService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
public class UserServiceTest {

    @Mock
    private UserRepository userRepository;

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
}
