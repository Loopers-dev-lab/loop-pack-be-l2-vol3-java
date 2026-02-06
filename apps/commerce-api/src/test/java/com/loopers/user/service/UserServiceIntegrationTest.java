package com.loopers.user.service;

import com.loopers.testcontainers.MySqlTestContainersConfig;
import com.loopers.user.domain.User;
import com.loopers.user.dto.CreateUserRequest;
import com.loopers.user.dto.GetMyInfoResponse;
import com.loopers.user.exception.DuplicateLoginIdException;
import com.loopers.user.exception.InvalidCredentialsException;
import com.loopers.user.exception.SamePasswordException;
import org.springframework.security.crypto.password.PasswordEncoder;
import com.loopers.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

@SpringBootTest
@Import(MySqlTestContainersConfig.class)
@Transactional
public class UserServiceIntegrationTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserService userService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    void 회원_가입시_User_저장이_수행된다() {
        //given
        CreateUserRequest request = new CreateUserRequest(
                "testuser", "password123!", "홍길동", "1990-04-27", "test@test.com"
        );

        //when
        User savedUser = userService.createUser(request);

        //then
        User foundUser = userRepository.findById(savedUser.getId()).orElseThrow();
        assertThat(foundUser.getLoginId()).isEqualTo(request.loginId());
        assertThat(foundUser.getName()).isEqualTo(request.name());
        assertThat(foundUser.getEmail()).isEqualTo(request.email());
    }

    @Test
    void 이미_가입된_ID로_회원가입_시도_시_DuplicateLoginIdException이_발생한다() {
        //given
        CreateUserRequest request = new CreateUserRequest(
                "testuser", "password123!", "홍길동", "1990-04-27", "test@test.com"
        );
        //testuser 라는 ID로 가입
        userService.createUser(request);

        //when
        //동일한 아이디로 가입하는 경우
        CreateUserRequest duplicateRequest = new CreateUserRequest(
                "testuser", "password456!", "김철수", "1995-01-01", "other@test.com"
        );
        Throwable thrown = catchThrowable(() -> userService.createUser(duplicateRequest));

        //then
        assertThat(thrown).isInstanceOf(DuplicateLoginIdException.class);
    }

    @Test
    void DB에_저장된_사용자_정보를_정상적으로_조회한다() {
        // given
        String loginId = "testuser";
        CreateUserRequest request = new CreateUserRequest(
                loginId, "password123!", "홍길동", "1990-01-01", "test@test.com"
        );
        userService.createUser(request);

        // when
        GetMyInfoResponse response = userService.getMyInfo(loginId);

        // then
        assertThat(response.loginId()).isEqualTo(loginId);
        assertThat(response.name()).isEqualTo("홍길*");
        assertThat(response.birthDate()).isEqualTo("1990-01-01");
        assertThat(response.email()).isEqualTo("test@test.com");
    }

    @Test
    void 존재하지_않는_로그인ID로_조회시_InvalidCredentialsException이_발생한다() {
        // given
        String nonExistentLoginId = "nonexistent";

        // when & then
        assertThatThrownBy(() -> userService.getMyInfo(nonExistentLoginId))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    void DB에_저장된_사용자의_비밀번호가_정상적으로_변경된다() {
        // given
        String loginId = "testuser";
        String currentPassword = "password123!";
        String newPassword = "newPassword456!";

        CreateUserRequest request = new CreateUserRequest(
                loginId, currentPassword, "홍길동", "1990-01-01", "test@test.com"
        );
        userService.createUser(request);

        // when
        userService.changePassword(loginId, currentPassword, newPassword);

        // then
        User updatedUser = userRepository.findByLoginId(loginId).orElseThrow();
        assertThat(passwordEncoder.matches(newPassword, updatedUser.getPassword())).isTrue();
    }

    @Test
    void 비밀번호_변경시_기존_비밀번호가_일치하지_않으면_InvalidCredentialsException이_발생한다() {
        // given
        String loginId = "testuser";
        String currentPassword = "password123!";
        String wrongPassword = "wrongPassword!";
        String newPassword = "newPassword456!";

        CreateUserRequest request = new CreateUserRequest(
                loginId, currentPassword, "홍길동", "1990-01-01", "test@test.com"
        );
        userService.createUser(request);

        // when & then
        assertThatThrownBy(() -> userService.changePassword(loginId, wrongPassword, newPassword))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    void 비밀번호_변경시_새_비밀번호가_기존과_동일하면_SamePasswordException이_발생한다() {
        // given
        String loginId = "testuser";
        String currentPassword = "password123!";

        CreateUserRequest request = new CreateUserRequest(
                loginId, currentPassword, "홍길동", "1990-01-01", "test@test.com"
        );
        userService.createUser(request);

        // when & then
        assertThatThrownBy(() -> userService.changePassword(loginId, currentPassword, currentPassword))
                .isInstanceOf(SamePasswordException.class);
    }
}
