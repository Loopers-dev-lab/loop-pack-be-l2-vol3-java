package com.loopers.user.service;

import com.loopers.testcontainers.MySqlTestContainersConfig;
import com.loopers.user.domain.User;
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
        String loginId = "testuser";
        String password = "password123!";
        String name = "홍길동";
        String birthDate = "1990-04-27";
        String email = "test@test.com";

        //when
        User savedUser = userService.createUser(loginId, password, name, birthDate, email);

        //then
        User foundUser = userRepository.findById(savedUser.getId()).orElseThrow();
        assertThat(foundUser.getLoginId()).isEqualTo(loginId);
        assertThat(foundUser.getName()).isEqualTo(name);
        assertThat(foundUser.getEmail()).isEqualTo(email);
    }

    @Test
    void 이미_가입된_ID로_회원가입_시도_시_실패() {
        //given
        String loginId = "testuser";
        String password = "password123!";
        String name = "홍길동";
        String birthDate = "1990-04-27";
        String email = "test@test.com";
        //testuser 라는 ID로 가입
        userService.createUser(loginId, password, name, birthDate, email);

        //when
        //동일한 아이디로 가입하는 경우
        Throwable thrown = catchThrowable(() -> userService.createUser(loginId, "password456!", "김철수", "1995-01-01", "other@test.com"));

        //then
        assertThat(thrown).isInstanceOf(IllegalArgumentException.class);
    }
}
