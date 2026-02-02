package com.loopers.domain.user;

import com.loopers.interfaces.api.user.dto.CreateUserRequestV1;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.time.LocalDate;

public class UserServiceTest {
    @Test
    @DisplayName("회원가입시 비밀번호를 암호화해서 저장한다.")
    void signUp_encryptsPassword() {
        // given
        UserRepository userRepository = new InMemoryUserRepository();
        PasswordEncoder passwordEncoder = raw -> "test_encrypt(" + raw + ")";
        UserService userService = new UserService(userRepository, passwordEncoder);

        String rawPassword = "passwordRaw123";
        CreateUserRequestV1 userJoinRequest = CreateUserRequestV1.builder()
                                                               .loginId("test_user")
                                                               .password(rawPassword)
                                                               .name("테스터")
                                                               .birthDate(LocalDate.of(2000, 1, 15))
                                                               .email("test@example.com")
                                                               .build();

        // when
        userService.signUp(userJoinRequest);

        // then
        User saved = userRepository.findByEmail(userJoinRequest.getEmail());
        Assertions.assertThat(saved.getPassword()).isNotEqualTo(userJoinRequest.getPassword());
    }
}
