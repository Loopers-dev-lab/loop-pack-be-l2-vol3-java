package com.loopers.domain.user;

import com.loopers.domain.user.PasswordEncryptor;
import com.loopers.domain.user.exception.InvalidPasswordException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class UserDomainServiceTest {

    private UserDomainService userDomainService;
    private PasswordEncryptor passwordEncryptor;

    @BeforeEach
    void setUp() {
        userDomainService = new UserDomainService();
        passwordEncryptor = mock(PasswordEncryptor.class);
    }

    @DisplayName("createUser: 유효한 정보로 사용자 생성에 성공한다")
    @Test
    void createUser_success() {
        // given
        String loginId = "testuser";
        String rawPasswordValue = "Password123!";
        RawPassword rawPassword = new RawPassword(rawPasswordValue);
        String name = "김테스트";
        LocalDate birthDate = LocalDate.of(2000, 1, 1);
        String email = "test@example.com";
        Gender gender = Gender.MALE;

        when(passwordEncryptor.encode(anyString())).thenReturn("ENCRYPTED_Password123!");

        // when
        User user = userDomainService.createUser(
            loginId,
            rawPassword,
            name,
            birthDate,
            email,
            gender,
            passwordEncryptor
        );

        // then
        assertThat(user).isNotNull();
        assertThat(user.getLoginId()).isEqualTo(loginId);
        assertThat(user.getName()).isEqualTo(name);
        assertThat(user.getBirthDate()).isEqualTo(birthDate);
        assertThat(user.getEmail()).isEqualTo(email);
        assertThat(user.getGender()).isEqualTo(gender);
        assertThat(user.getPassword()).isEqualTo("ENCRYPTED_Password123!");
    }

    @DisplayName("createUser: 비밀번호에 생년월일이 포함되면 예외가 발생한다")
    @Test
    void createUser_fail_passwordContainsBirthDate() {
        // given
        String loginId = "testuser";
        LocalDate birthDate = LocalDate.of(2000, 1, 1);
        String invalidPasswordValue = "Pw20000101!";  // 생년월일 포함
        RawPassword invalidPassword = new RawPassword(invalidPasswordValue);
        String name = "김테스트";
        String email = "test@example.com";
        Gender gender = Gender.MALE;

        when(passwordEncryptor.encode(invalidPasswordValue)).thenReturn("ENCRYPTED_Pw20000101!");

        // when & then
        assertThatThrownBy(() ->
            userDomainService.createUser(
                loginId,
                invalidPassword,
                name,
                birthDate,
                email,
                gender,
                passwordEncryptor
            )
        )
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("생년월일은 비밀번호 내에 포함될 수 없습니다");
    }

    @DisplayName("updatePassword: 유효한 비밀번호로 변경에 성공한다")
    @Test
    void updatePassword_success() {
        // given
        User user = createTestUser();
        String oldPasswordValue = "OldPassword1!";
        String newPasswordValue = "NewPassword2!";
        RawPassword oldPassword = new RawPassword(oldPasswordValue);
        RawPassword newPassword = new RawPassword(newPasswordValue);

        when(passwordEncryptor.matches(oldPasswordValue, "ENCRYPTED_OldPassword1!")).thenReturn(true);
        when(passwordEncryptor.encode(newPasswordValue)).thenReturn("ENCRYPTED_NewPassword2!");

        // when
        userDomainService.updatePassword(user, oldPassword, newPassword, passwordEncryptor);

        // then
        assertThat(user.getPassword()).isEqualTo("ENCRYPTED_NewPassword2!");
    }

    @DisplayName("updatePassword: 기존 비밀번호가 일치하지 않으면 예외가 발생한다")
    @Test
    void updatePassword_fail_wrongOldPassword() {
        // given
        User user = createTestUser();
        String wrongOldPasswordValue = "WrongPassword1!";
        String newPasswordValue = "NewPassword2!";
        RawPassword wrongOldPassword = new RawPassword(wrongOldPasswordValue);
        RawPassword newPassword = new RawPassword(newPasswordValue);

        when(passwordEncryptor.matches(wrongOldPasswordValue, "ENCRYPTED_OldPassword1!")).thenReturn(false);

        // when & then
        assertThatThrownBy(() ->
            userDomainService.updatePassword(user, wrongOldPassword, newPassword, passwordEncryptor)
        )
            .isInstanceOf(InvalidPasswordException.class);
    }

    @DisplayName("updatePassword: 새 비밀번호에 생년월일이 포함되면 예외가 발생한다")
    @Test
    void updatePassword_fail_newPasswordContainsBirthDate() {
        // given
        User user = createTestUser();
        String oldPasswordValue = "OldPassword1!";
        String invalidNewPasswordValue = "Pw20000101!";  // 생년월일 포함
        RawPassword oldPassword = new RawPassword(oldPasswordValue);
        RawPassword invalidNewPassword = new RawPassword(invalidNewPasswordValue);

        when(passwordEncryptor.matches(oldPasswordValue, "ENCRYPTED_OldPassword1!")).thenReturn(true);
        when(passwordEncryptor.encode(invalidNewPasswordValue)).thenReturn("ENCRYPTED_Pw20000101!");

        // when & then
        assertThatThrownBy(() ->
            userDomainService.updatePassword(user, oldPassword, invalidNewPassword, passwordEncryptor)
        )
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("생년월일은 비밀번호 내에 포함될 수 없습니다");
    }

    private User createTestUser() {
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
}
