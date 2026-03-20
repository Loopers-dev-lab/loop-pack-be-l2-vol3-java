package com.loopers.domain.user;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("UserService 도메인 서비스 테스트")
class UserServiceTest {

    @Mock
    UserRepository userRepository;

    @Mock
    PasswordEncoder passwordEncoder;

    @InjectMocks
    UserService userService;

    // === 회원가입 ===

    @Nested
    @DisplayName("회원가입")
    class RegisterTests {

        @Test
        @DisplayName("유효한 입력으로 회원가입 성공 시 UserModel을 반환한다")
        void register_WithValidInput_ShouldReturnUserModel() {
            when(userRepository.existsByLoginId("testuser01")).thenReturn(false);
            when(passwordEncoder.encode("Test1234!@#")).thenReturn("{bcrypt}encoded");
            when(userRepository.save(any(UserModel.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            UserModel result = userService.register(
                    new UserRegisterCommand("testuser01", "Test1234!@#", "홍길동", "19900101", "a@b.com", "서울")
            );

            assertThat(result.getLoginId()).isEqualTo("testuser01");
            assertThat(result.getMaskedName()).isEqualTo("홍길*");
            assertThat(result.getBirthday()).isEqualTo("19900101");
            assertThat(result.getEmail()).isEqualTo("a@b.com");
            assertThat(result.getAddress()).isEqualTo("서울");
            verify(userRepository).existsByLoginId("testuser01");
            verify(passwordEncoder).encode("Test1234!@#");
            verify(userRepository).save(any(UserModel.class));
        }

        @Test
        @DisplayName("중복 loginId로 가입 시 DUPLICATE_USER_ID 발생")
        void register_WithDuplicateLoginId_ShouldThrow_DUPLICATE_USER_ID() {
            when(userRepository.existsByLoginId("duplicate")).thenReturn(true);

            assertThatThrownBy(() -> userService.register(
                    new UserRegisterCommand("duplicate", "Test1234!@#", "홍길동", "19900101", "a@b.com", "서울")
            ))
                    .isInstanceOf(CoreException.class)
                    .satisfies(ex -> assertThat(((CoreException) ex).getErrorType())
                            .isEqualTo(ErrorType.DUPLICATE_USER_ID));

            verify(userRepository, never()).save(any());
        }
    }

    // === 조회 ===

    @Nested
    @DisplayName("조회")
    class FindTests {

        @Test
        @DisplayName("ID로 사용자 조회 성공")
        void findByUserId_Existing_ShouldReturn() {
            UserModel user = createTestUser();
            when(userRepository.findByUserId(1L)).thenReturn(Optional.of(user));

            UserModel result = userService.findByUserId(1L);

            assertThat(result.getLoginId()).isEqualTo("testuser01");
        }

        @Test
        @DisplayName("존재하지 않는 ID 조회 시 USER_NOT_FOUND")
        void findByUserId_NotFound_ShouldThrow_USER_NOT_FOUND() {
            when(userRepository.findByUserId(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> userService.findByUserId(999L))
                    .isInstanceOf(CoreException.class)
                    .satisfies(ex -> assertThat(((CoreException) ex).getErrorType())
                            .isEqualTo(ErrorType.USER_NOT_FOUND));
        }

        @Test
        @DisplayName("loginId로 사용자 조회 성공")
        void findByLoginId_Existing_ShouldReturn() {
            UserModel user = createTestUser();
            when(userRepository.findByLoginId("testuser01")).thenReturn(Optional.of(user));

            UserModel result = userService.findByLoginId("testuser01");

            assertThat(result.getLoginId()).isEqualTo("testuser01");
        }

        @Test
        @DisplayName("존재하지 않는 loginId 조회 시 USER_NOT_FOUND")
        void findByLoginId_NotFound_ShouldThrow_USER_NOT_FOUND() {
            when(userRepository.findByLoginId("nonexistent")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> userService.findByLoginId("nonexistent"))
                    .isInstanceOf(CoreException.class)
                    .satisfies(ex -> assertThat(((CoreException) ex).getErrorType())
                            .isEqualTo(ErrorType.USER_NOT_FOUND));
        }
    }

    // === 인증 ===

    @Nested
    @DisplayName("인증")
    class AuthenticateTests {

        @Test
        @DisplayName("올바른 비밀번호로 인증 성공")
        void authenticate_WithCorrectPassword_ShouldReturnUser() {
            UserModel user = createTestUser();
            when(userRepository.findByLoginId("testuser01")).thenReturn(Optional.of(user));
            when(passwordEncoder.matches("Test1234!@#", "{bcrypt}encoded")).thenReturn(true);

            UserModel result = userService.authenticate("testuser01", "Test1234!@#");

            assertThat(result.getLoginId()).isEqualTo("testuser01");
        }

        @Test
        @DisplayName("잘못된 비밀번호로 인증 실패")
        void authenticate_WithWrongPassword_ShouldThrow() {
            UserModel user = createTestUser();
            when(userRepository.findByLoginId("testuser01")).thenReturn(Optional.of(user));
            when(passwordEncoder.matches("WrongPass123!", "{bcrypt}encoded")).thenReturn(false);

            assertThatThrownBy(() -> userService.authenticate("testuser01", "WrongPass123!"))
                    .isInstanceOf(CoreException.class)
                    .satisfies(ex -> assertThat(((CoreException) ex).getErrorType())
                            .isEqualTo(ErrorType.UNAUTHORIZED));
        }
    }

    // === 내 정보 조회 ===

    @Nested
    @DisplayName("내 정보 조회")
    class GetMyInfoTests {

        @Test
        @DisplayName("인증 후 UserModel을 반환한다")
        void getMyInfo_ShouldReturnUserModel() {
            UserModel user = createTestUser();
            when(userRepository.findByLoginId("testuser01")).thenReturn(Optional.of(user));
            when(passwordEncoder.matches("Test1234!@#", "{bcrypt}encoded")).thenReturn(true);

            UserModel result = userService.getMyInfo("testuser01", "Test1234!@#");

            assertThat(result.getLoginId()).isEqualTo("testuser01");
            assertThat(result.getMaskedName()).isEqualTo("홍길*");
        }
    }

    // === 비밀번호 변경 ===

    @Nested
    @DisplayName("비밀번호 변경")
    class ChangePasswordTests {

        @Test
        @DisplayName("유효한 조건으로 비밀번호 변경 성공")
        void changePassword_WithCorrectCurrentPw_ShouldUpdate() {
            UserModel user = createTestUser();
            when(userRepository.findByLoginId("testuser01")).thenReturn(Optional.of(user));
            when(passwordEncoder.matches("Test1234!@#", "{bcrypt}encoded")).thenReturn(true);
            when(passwordEncoder.matches("NewPass5678$", "{bcrypt}encoded")).thenReturn(false);
            when(passwordEncoder.encode("NewPass5678$")).thenReturn("{bcrypt}newencoded");

            userService.changePassword("testuser01", "Test1234!@#", "NewPass5678$");

            assertThat(user.getPassword()).isEqualTo("{bcrypt}newencoded");
        }

        @Test
        @DisplayName("현재 비밀번호 불일치 시 실패")
        void changePassword_WithWrongCurrentPw_ShouldThrow() {
            UserModel user = createTestUser();
            when(userRepository.findByLoginId("testuser01")).thenReturn(Optional.of(user));
            when(passwordEncoder.matches("WrongPass!", "{bcrypt}encoded")).thenReturn(false);

            assertThatThrownBy(() -> userService.changePassword("testuser01", "WrongPass!", "NewPass5678$"))
                    .isInstanceOf(CoreException.class)
                    .satisfies(ex -> assertThat(((CoreException) ex).getErrorType())
                            .isEqualTo(ErrorType.PASSWORD_MISMATCH));
        }

        @Test
        @DisplayName("새 비밀번호가 기존과 동일하면 실패")
        void changePassword_SameAsOld_ShouldThrow() {
            UserModel user = createTestUser();
            when(userRepository.findByLoginId("testuser01")).thenReturn(Optional.of(user));
            when(passwordEncoder.matches("Test1234!@#", "{bcrypt}encoded")).thenReturn(true);

            assertThatThrownBy(() -> userService.changePassword("testuser01", "Test1234!@#", "Test1234!@#"))
                    .isInstanceOf(CoreException.class)
                    .satisfies(ex -> assertThat(((CoreException) ex).getErrorType())
                            .isEqualTo(ErrorType.SAME_PASSWORD));
        }

        @Test
        @DisplayName("인증 후 비밀번호 변경 성공")
        void authenticateAndChangePassword_ShouldAuthenticateAndChange() {
            UserModel user = createTestUser();
            when(userRepository.findByLoginId("testuser01")).thenReturn(Optional.of(user));
            when(passwordEncoder.matches("Test1234!@#", "{bcrypt}encoded")).thenReturn(true);
            when(passwordEncoder.matches("NewPass5678$", "{bcrypt}encoded")).thenReturn(false);
            when(passwordEncoder.encode("NewPass5678$")).thenReturn("{bcrypt}newencoded");

            userService.authenticateAndChangePassword("testuser01", "Test1234!@#",
                    "Test1234!@#", "NewPass5678$");

            assertThat(user.getPassword()).isEqualTo("{bcrypt}newencoded");
        }
    }

    // === Helper ===

    private UserModel createTestUser() {
        return UserModel.createWithEncodedPassword(
                "testuser01", "{bcrypt}encoded", "홍길동", "19900101", "a@b.com", "서울"
        );
    }
}
