package com.loopers.domain.user;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@SpringBootTest
class UserServiceIntegrationTest {

    @Autowired
    private UserService userService;

    @Autowired
    private UserRepository userRepository;

    @SpyBean
    private UserRepository userRepositorySpy;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("회원가입 할 때")
    @Nested
    class Register {

        @DisplayName("회원 가입시 User 저장이 수행된다 (spy 검증)")
        @Test
        void saveUser_whenRegister() {
            // given
            String loginId = "testuser";
            String password = "password1";
            String name = "김윤선";
            LocalDate birthDate = LocalDate.of(1997, 10, 8);
            String email = "test@example.com";

            // when
            User savedUser = userService.register(loginId, password, name, birthDate, email);

            // then
            verify(userRepositorySpy, times(1)).save(any(User.class));

            // 비밀번호가 BCrypt로 암호화되었는지 검증 (BCrypt는 $2a$ 또는 $2b$로 시작)
            assertThat(savedUser.getPassword()).isNotEqualTo(password); // 평문이 아님
            assertThat(savedUser.getPassword()).startsWith("$2"); // BCrypt 형식
            assertThat(savedUser.getPassword()).hasSize(60); // BCrypt는 60자 고정
        }

        @DisplayName("이미 가입된 ID로 회원가입 시도 시, 실패한다")
        @Test
        void fail_whenDuplicateLoginId() {
            // given - 먼저 사용자 등록 (서비스 통해 정상 가입)
            String duplicateId = "duplicate";
            userService.register(duplicateId, "password1", "기존유저", LocalDate.of(1990, 1, 1), "existing@example.com");

            // when & then - 같은 ID로 다시 가입 시도
            assertThatThrownBy(() -> {
                userService.register(duplicateId, "newpass12", "새유저", LocalDate.of(1995, 5, 5), "new@example.com");
            })
                .isInstanceOf(CoreException.class)
                .satisfies(e -> {
                    CoreException ce = (CoreException) e;
                    assertThat(ce.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
                    assertThat(ce.getMessage()).contains("이미 가입된 ID입니다");
                });
        }
    }
}
