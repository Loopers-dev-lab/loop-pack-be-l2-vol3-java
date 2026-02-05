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
        User savedUser = userService.signUp(loginId, password, name, birthDate, email);

        //then
        User foundUser = userRepository.findById(savedUser.getId()).orElseThrow();
        assertThat(foundUser.getLoginId()).isEqualTo(loginId);
        assertThat(foundUser.getName()).isEqualTo(name);
        assertThat(foundUser.getEmail()).isEqualTo(email);
    }
}
