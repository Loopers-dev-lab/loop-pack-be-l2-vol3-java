package com.loopers.domain.user;

import com.loopers.application.user.UserInfo;
import com.loopers.infrastructure.user.BcryptPasswordEncoder;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class UserServiceTest {
    private InMemoryUserRepository userRepository;
    private PasswordEncoder passwordEncoder;
    private UserService userService;

    @BeforeEach
    void setUp() {
        userRepository = new InMemoryUserRepository();
        passwordEncoder = new BcryptPasswordEncoder();
        userService = new UserService(userRepository, passwordEncoder);
    }

    @DisplayName("내 정보 조회 시 이름의 마지막 글자는 마스킹(*)되어 반환된다")
    @Test
    void getMyInfo_masks_last_character_of_name() {
        // given
        User user = UserFixture.builder()
                               .name("테스터")
                               .build();

        userRepository.save(user);

        // when
        UserInfo myInfo = userService.getMyInfo(user.getLoginId());

        // then
        assertThat(myInfo.name()).isEqualTo("테스*");
        assertThat(myInfo.loginId()).isEqualTo(user.getLoginId());
        assertThat(myInfo.email()).isEqualTo(user.getEmail());
        assertThat(myInfo.birthDate()).isEqualTo(user.getBirthDate());
    }

    @DisplayName("새 비밀번호가 현재 비밀번호와 같으면, BAD_REQUEST 예외가 발생한다.")
    @Test
    void throwsException_whenNewPasswordSameAsCurrent() {
        // given
        String rawPassword = "ValidPass1!";
        String encodedPassword = passwordEncoder.encode(rawPassword);
        User user = UserFixture.builder()
                               .password(encodedPassword)
                               .build();
        userRepository.save(user);

        UpdatePasswordCommand command = new UpdatePasswordCommand(user.getLoginId(), rawPassword);

        // when
        CoreException result = assertThrows(CoreException.class, () -> {
            userService.updatePassword(command);
        });

        // then
        assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
    }
}
