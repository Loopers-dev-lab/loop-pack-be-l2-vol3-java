package com.loopers.user.service;

import com.loopers.testcontainers.MySqlTestContainersConfig;
import com.loopers.user.domain.User;
import com.loopers.user.dto.CreateUserRequest;
import com.loopers.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

@SpringBootTest
@Import(MySqlTestContainersConfig.class)
@Transactional
public class UserServiceIntegrationTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserService userService;

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
    void 이미_가입된_ID로_회원가입_시도_시_실패() {
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
        assertThat(thrown).isInstanceOf(IllegalArgumentException.class);
    }
}
