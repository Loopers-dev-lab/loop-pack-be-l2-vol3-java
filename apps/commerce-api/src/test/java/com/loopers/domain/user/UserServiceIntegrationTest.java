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
import java.util.Optional;

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
            Gender gender = Gender.FEMALE;

            // when
            User savedUser = userService.register(loginId, password, name, birthDate, email, gender);

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
            userService.register(duplicateId, "password1", "기존유저", LocalDate.of(1990, 1, 1), "existing@example.com", Gender.MALE);

            // when & then - 같은 ID로 다시 가입 시도
            assertThatThrownBy(() -> {
                userService.register(duplicateId, "newpass12", "새유저", LocalDate.of(1995, 5, 5), "new@example.com", Gender.FEMALE);
            })
                .isInstanceOf(CoreException.class)
                .satisfies(e -> {
                    CoreException ce = (CoreException) e;
                    assertThat(ce.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
                    assertThat(ce.getMessage()).contains("이미 가입된 ID입니다");
                });
        }
    }

    @DisplayName("내 정보 조회 할 때")
    @Nested
    class GetUserInfo {

        @DisplayName("해당 ID의 회원이 존재할 경우, 회원 정보가 반환된다")
        @Test
        void getUserInfo_success() {
            // given - 회원 등록
            String loginId = "testuser";
            String password = "password1";
            String name = "김윤선";
            LocalDate birthDate = LocalDate.of(1997, 10, 8);
            String email = "test@example.com";
            Gender gender = Gender.FEMALE;

            User savedUser = userService.register(loginId, password, name, birthDate, email, gender);

            // when - 조회
            Optional<User> result = userService.getUserInfo(savedUser.getId());

            // then - 존재 확인
            assertThat(result).isPresent();
            assertThat(result.get().getLoginId()).isEqualTo(loginId);
            assertThat(result.get().getName()).isEqualTo(name);
            assertThat(result.get().getEmail()).isEqualTo(email);
        }

        @DisplayName("해당 ID의 회원이 존재하지 않을 경우, Optional.empty()가 반환된다")
        @Test
        void getUserInfo_notFound() {
            // when - 존재하지 않는 ID로 조회
            Optional<User> result = userService.getUserInfo(999999L);

            // then - empty 확인
            assertThat(result).isEmpty();
        }
    }

    @DisplayName("비밀번호 수정 할 때")
    @Nested
    class ChangePassword {

        @DisplayName("기존 비밀번호가 일치하면, 비밀번호가 변경된다")
        @Test
        void changePassword_success() {
            // given - 회원 등록
            String loginId = "testuser";
            String oldPassword = "password1";
            LocalDate birthDate = LocalDate.of(1997, 10, 8);
            User savedUser = userService.register(loginId, oldPassword, "김윤선", birthDate, "test@example.com", Gender.FEMALE);

            // when - 비밀번호 변경 (1997년생 -> 20대만 가능)
            String newPassword = "newpass2!";
            userService.changePassword(savedUser.getId(), oldPassword, newPassword);

            // then - 새 비밀번호로 암호화되어 저장되었는지 확인
            User updatedUser = userRepository.findById(savedUser.getId()).orElseThrow();
            assertThat(updatedUser.getPassword()).isNotEqualTo(oldPassword);
            assertThat(updatedUser.getPassword()).isNotEqualTo(savedUser.getPassword());
            assertThat(updatedUser.getPassword()).startsWith("$2"); // BCrypt 형식
        }

        @DisplayName("기존 비밀번호가 일치하지 않으면, 실패한다")
        @Test
        void changePassword_fail_whenWrongOldPassword() {
            // given
            String loginId = "testuser";
            String oldPassword = "password1";
            User savedUser = userService.register(loginId, oldPassword, "김윤선", LocalDate.of(1997, 10, 8), "test@example.com", Gender.FEMALE);

            // when & then
            assertThatThrownBy(() -> {
                userService.changePassword(savedUser.getId(), "wrongPassword", "newpass2!");
            })
                .isInstanceOf(CoreException.class)
                .satisfies(e -> {
                    CoreException ce = (CoreException) e;
                    assertThat(ce.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
                    assertThat(ce.getMessage()).contains("기존 비밀번호가 일치하지 않습니다");
                });
        }

        @DisplayName("사용자가 존재하지 않으면, 실패한다")
        @Test
        void changePassword_fail_whenUserNotFound() {
            // when & then
            assertThatThrownBy(() -> {
                userService.changePassword(999999L, "oldpass1", "newpass2!");
            })
                .isInstanceOf(CoreException.class)
                .satisfies(e -> {
                    CoreException ce = (CoreException) e;
                    assertThat(ce.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
                    assertThat(ce.getMessage()).contains("사용자를 찾을 수 없습니다");
                });
        }
    }
}
