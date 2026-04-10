package com.loopers.domain.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.loopers.support.error.CoreException;
import java.util.ArrayList;
import java.util.List;
import org.springframework.context.ApplicationEventPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("STEP3: UserService Fake 기반 단위 테스트 (After)")
class UserServiceFakeTest {

    private FakeUserRepository userRepository;
    private FakePasswordEncoder passwordEncoder;
    private List<Object> publishedEvents;
    private UserService userService;

    private String loginId;
    private String rawPassword;
    private String name;
    private String birthDate;
    private String email;

    @BeforeEach
    void setUp() {
        userRepository = new FakeUserRepository();
        passwordEncoder = new FakePasswordEncoder();
        publishedEvents = new ArrayList<>();
        ApplicationEventPublisher publisher = publishedEvents::add;
        userService = new UserService(userRepository, passwordEncoder, publisher);

        loginId = "testuser1";
        rawPassword = "Test1234!@#";
        name = "홍길동";
        birthDate = "19900115";
        email = "test@example.com";
    }

    @DisplayName("회원가입")
    @Nested
    class Signup {

        @Test
        @DisplayName("성공 — when-then 0줄, 암호화 결과를 직접 검증")
        void signup_성공() {
            // act
            UserModel result = userService.signup(loginId, rawPassword, name, birthDate, email);

            // assert
            assertThat(result).isNotNull();
            assertThat(result.getLoginId()).isEqualTo(loginId);

            // 암호화 검증은 repository를 통해 직접 확인
            UserModel saved = userRepository.findByLoginId(loginId).orElseThrow();
            assertThat(saved.getPassword()).isEqualTo("ENCODED_Test1234!@#");
        }

        @Test
        @DisplayName("중복 아이디면 예외")
        void signup_중복아이디_예외() {
            // arrange
            userService.signup(loginId, rawPassword, name, birthDate, email);

            // act & assert
            assertThatThrownBy(() ->
                userService.signup(loginId, "Other123!@#", name, birthDate, email)
            ).isInstanceOf(CoreException.class)
             .hasMessageContaining("이미 존재하는 아이디입니다.");
        }

        @Test
        @DisplayName("비밀번호 형식이 올바르지 않으면 예외가 발생한다.")
        void signup_비밀번호_형식_오류() {
            assertThatThrownBy(() ->
                userService.signup(loginId, "short", name, birthDate, email)
            ).isInstanceOf(CoreException.class)
             .hasMessageContaining("비밀번호는 8~16자의 영문 대소문자, 숫자, 특수문자 조합이어야 합니다.");
        }

        @Test
        @DisplayName("비밀번호에 생년월일이 포함되면 예외가 발생한다.")
        void signup_비밀번호에_생년월일_포함() {
            assertThatThrownBy(() ->
                userService.signup(loginId, "Pw19900115!", name, birthDate, email)
            ).isInstanceOf(CoreException.class)
             .hasMessageContaining("생년월일은 비밀번호 내에 포함될 수 없습니다.");
        }
    }

    @DisplayName("비밀번호 변경")
    @Nested
    class ChangePassword {

        @Test
        @DisplayName("성공 — when-then 0줄, 변경된 비밀번호를 직접 검증")
        void changePassword_성공() {
            // arrange
            userService.signup(loginId, rawPassword, name, birthDate, email);

            // act
            userService.changePassword(loginId, rawPassword, "NewPass123!@");

            // assert
            UserModel updated = userRepository.findByLoginId(loginId).orElseThrow();
            assertThat(updated.getPassword()).isEqualTo("ENCODED_NewPass123!@");
        }

        @Test
        @DisplayName("현재 비밀번호 불일치면 예외")
        void changePassword_현재비밀번호_불일치() {
            // arrange
            userService.signup(loginId, rawPassword, name, birthDate, email);

            // act & assert
            assertThatThrownBy(() ->
                userService.changePassword(loginId, "Wrong123!@#", "NewPass123!@")
            ).isInstanceOf(CoreException.class)
             .hasMessageContaining("현재 비밀번호가 일치하지 않습니다.");
        }

        @Test
        @DisplayName("새 비밀번호가 현재와 같으면 예외")
        void changePassword_새비밀번호가_현재와_같으면_예외() {
            // arrange
            userService.signup(loginId, rawPassword, name, birthDate, email);

            // act & assert
            assertThatThrownBy(() ->
                userService.changePassword(loginId, rawPassword, rawPassword)
            ).isInstanceOf(CoreException.class)
             .hasMessageContaining("현재 사용 중인 비밀번호는 사용할 수 없습니다.");
        }

        @Test
        @DisplayName("새 비밀번호에 생년월일이 포함되면 예외가 발생한다.")
        void changePassword_새비밀번호에_생년월일_포함() {
            // arrange
            userService.signup(loginId, rawPassword, name, birthDate, email);

            // act & assert
            assertThatThrownBy(() ->
                userService.changePassword(loginId, rawPassword, "Pw19900115!")
            ).isInstanceOf(CoreException.class)
             .hasMessageContaining("생년월일은 비밀번호 내에 포함될 수 없습니다.");
        }
    }
}
