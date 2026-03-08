package com.loopers.application.user;

import com.loopers.domain.user.*;
import com.loopers.domain.user.PasswordEncryptor;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class UserFacadeTest {

    private UserFacade userFacade;
    private UserRepository userRepository;
    private PasswordEncryptor passwordEncryptor;
    private UserService userService;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        passwordEncryptor = mock(PasswordEncryptor.class);
        userService = new UserService();

        userFacade = new UserFacade(
            userRepository,
            passwordEncryptor,
            userService
        );
    }

    @DisplayName("register: 신규 사용자 등록에 성공한다")
    @Test
    void register_success() {
        // given
        String loginId = "newuser";
        String password = "Password123!";
        String name = "김신규";
        LocalDate birthDate = LocalDate.of(2000, 1, 1);
        String email = "new@example.com";
        Gender gender = Gender.MALE;

        when(userRepository.existsByLoginId(loginId)).thenReturn(false);
        when(passwordEncryptor.encode(password)).thenReturn("ENCRYPTED_Password123!");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // when
        UserDto.UserInfo userInfo = userFacade.register(loginId, password, name, birthDate, email, gender);

        // then
        assertThat(userInfo).isNotNull();
        assertThat(userInfo.loginId()).isEqualTo(loginId);
        assertThat(userInfo.name()).isEqualTo(name);
        assertThat(userInfo.email()).isEqualTo(email);

        verify(userRepository).existsByLoginId(loginId);
        verify(userRepository).save(any(User.class));
        verify(passwordEncryptor).encode(password);
    }

    @DisplayName("register: 중복된 로그인 ID로 가입 시 예외가 발생한다")
    @Test
    void register_fail_duplicateLoginId() {
        // given
        String duplicateLoginId = "existing";
        String password = "Password123!";
        String name = "김중복";
        LocalDate birthDate = LocalDate.of(2000, 1, 1);
        String email = "duplicate@example.com";
        Gender gender = Gender.MALE;

        when(userRepository.existsByLoginId(duplicateLoginId)).thenReturn(true);

        // when & then
        assertThatThrownBy(() ->
            userFacade.register(duplicateLoginId, password, name, birthDate, email, gender)
        )
            .isInstanceOf(CoreException.class)
            .satisfies(e -> assertThat(((CoreException) e).getErrorType())
                .isEqualTo(ErrorType.DUPLICATE_LOGIN_ID));

        verify(userRepository).existsByLoginId(duplicateLoginId);
        verify(userRepository, never()).save(any(User.class));
    }

    @DisplayName("register: 비밀번호에 생년월일이 포함되면 예외가 발생한다")
    @Test
    void register_fail_passwordContainsBirthDate() {
        // given
        String loginId = "newuser";
        LocalDate birthDate = LocalDate.of(2000, 1, 1);
        String invalidPassword = "Pw20000101!";  // 생년월일 포함
        String name = "김신규";
        String email = "new@example.com";
        Gender gender = Gender.MALE;

        when(userRepository.existsByLoginId(loginId)).thenReturn(false);
        when(passwordEncryptor.encode(invalidPassword)).thenReturn("ENCRYPTED_Pw20000101!");

        // when & then
        assertThatThrownBy(() ->
            userFacade.register(loginId, invalidPassword, name, birthDate, email, gender)
        )
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("생년월일은 비밀번호 내에 포함될 수 없습니다");

        verify(userRepository).existsByLoginId(loginId);
        verify(userRepository, never()).save(any(User.class));
    }

    @DisplayName("getUserInfo: 사용자 조회에 성공한다")
    @Test
    void getUserInfo_success() {
        // given
        Long userId = 1L;
        User expectedUser = createTestUser(userId);

        when(userRepository.findById(userId)).thenReturn(Optional.of(expectedUser));

        // when
        Optional<UserDto.UserInfo> result = userFacade.getUserInfo(userId);

        // then
        assertThat(result).isPresent();
        assertThat(result.get().id()).isEqualTo(userId);
        assertThat(result.get().loginId()).isEqualTo("testuser");

        verify(userRepository).findById(userId);
    }

    @DisplayName("getUserInfo: 존재하지 않는 사용자 조회 시 빈 Optional을 반환한다")
    @Test
    void getUserInfo_notFound() {
        // given
        Long userId = 999L;

        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        // when
        Optional<UserDto.UserInfo> result = userFacade.getUserInfo(userId);

        // then
        assertThat(result).isEmpty();

        verify(userRepository).findById(userId);
    }

    @DisplayName("changePassword: 비밀번호 변경에 성공한다")
    @Test
    void changePassword_success() {
        // given
        Long userId = 1L;
        User user = createTestUser(userId);
        String oldPassword = "OldPassword1!";
        String newPassword = "NewPassword2!";

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(passwordEncryptor.matches(oldPassword, "ENCRYPTED_OldPassword1!")).thenReturn(true);
        when(passwordEncryptor.encode(newPassword)).thenReturn("ENCRYPTED_NewPassword2!");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // when
        userFacade.changePassword(userId, oldPassword, newPassword);

        // then
        // UserService에서 비밀번호 변경 로직을 이미 테스트했으므로,
        // 여기서는 올바른 순서로 메서드가 호출되었는지만 검증
        verify(userRepository).findById(userId);
        verify(passwordEncryptor).matches(oldPassword, "ENCRYPTED_OldPassword1!");
        verify(passwordEncryptor).encode(newPassword);
        verify(userRepository).save(user);
    }

    @DisplayName("changePassword: 존재하지 않는 사용자의 비밀번호 변경 시 예외가 발생한다")
    @Test
    void changePassword_fail_userNotFound() {
        // given
        Long userId = 999L;
        String oldPassword = "OldPassword1!";
        String newPassword = "NewPassword2!";

        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() ->
            userFacade.changePassword(userId, oldPassword, newPassword)
        )
            .isInstanceOf(CoreException.class)
            .satisfies(e -> assertThat(((CoreException) e).getErrorType())
                .isEqualTo(ErrorType.USER_NOT_FOUND));

        verify(userRepository).findById(userId);
        verify(userRepository, never()).save(any(User.class));
    }

    @DisplayName("changePassword: 기존 비밀번호가 일치하지 않으면 예외가 발생한다")
    @Test
    void changePassword_fail_wrongOldPassword() {
        // given
        Long userId = 1L;
        User user = createTestUser(userId);
        String wrongOldPassword = "WrongPassword1!";
        String newPassword = "NewPassword2!";

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(passwordEncryptor.matches(wrongOldPassword, "ENCRYPTED_OldPassword1!")).thenReturn(false);

        // when & then
        assertThatThrownBy(() ->
            userFacade.changePassword(userId, wrongOldPassword, newPassword)
        )
            .isInstanceOf(CoreException.class)
            .satisfies(e -> assertThat(((CoreException) e).getErrorType())
                .isEqualTo(ErrorType.INVALID_PASSWORD));

        verify(userRepository).findById(userId);
        verify(passwordEncryptor).matches(wrongOldPassword, "ENCRYPTED_OldPassword1!");
        verify(userRepository, never()).save(any(User.class));
    }

    @DisplayName("changePassword: 새 비밀번호에 생년월일이 포함되면 예외가 발생한다")
    @Test
    void changePassword_fail_newPasswordContainsBirthDate() {
        // given
        Long userId = 1L;
        User user = createRealTestUser();
        String oldPassword = "OldPassword1!";
        String invalidNewPassword = "Pw20000101!";  // 생년월일 포함

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(passwordEncryptor.matches(oldPassword, "ENCRYPTED_OldPassword1!")).thenReturn(true);
        when(passwordEncryptor.encode(invalidNewPassword)).thenReturn("ENCRYPTED_Pw20000101!");

        // when & then
        assertThatThrownBy(() ->
            userFacade.changePassword(userId, oldPassword, invalidNewPassword)
        )
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("생년월일은 비밀번호 내에 포함될 수 없습니다");

        verify(userRepository).findById(userId);
        verify(userRepository, never()).save(any(User.class));
    }

    private User createRealTestUser() {
        RawPassword rawPassword = new RawPassword("OldPassword1!");
        EncryptedPassword encryptedPassword = new EncryptedPassword("ENCRYPTED_OldPassword1!");

        return User.create(
            "testuser",
            rawPassword,
            encryptedPassword,
            "김테스트",
            LocalDate.of(2000, 1, 1),
            "test@example.com",
            Gender.MALE
        );
    }

    private User createTestUser(Long id) {
        RawPassword rawPassword = new RawPassword("OldPassword1!");
        EncryptedPassword encryptedPassword = new EncryptedPassword("ENCRYPTED_OldPassword1!");

        User user = User.create(
            "testuser",
            rawPassword,
            encryptedPassword,
            "김테스트",
            LocalDate.of(2000, 1, 1),
            "test@example.com",
            Gender.MALE
        );

        // Mock을 사용하여 ID를 가진 User 생성
        User mockedUser = mock(User.class);
        when(mockedUser.getId()).thenReturn(id);
        when(mockedUser.getLoginId()).thenReturn(user.getLoginId());
        when(mockedUser.getName()).thenReturn(user.getName());
        when(mockedUser.getBirthDate()).thenReturn(user.getBirthDate());
        when(mockedUser.getEmail()).thenReturn(user.getEmail());
        when(mockedUser.getGender()).thenReturn(user.getGender());
        when(mockedUser.getPassword()).thenReturn(user.getPassword());

        return mockedUser;
    }
}
