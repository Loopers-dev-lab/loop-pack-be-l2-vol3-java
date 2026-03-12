package com.loopers.domain.member;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MemberModelTest {

    @Mock
    private PasswordEncoder passwordEncoder;

    @DisplayName("회원 생성")
    @Nested
    class Create {

        @DisplayName("유효한 정보로 생성할 수 있다")
        @Test
        void createsWithValidInput() {
            // given
            LoginId loginId = new LoginId("kwonmo");
            String encodedPassword = "encodedPassword";
            MemberName name = new MemberName("양권모");
            LocalDate birthDate = LocalDate.of(1998, 9, 16);
            Email email = new Email("kwonmo@example.com");
            when(passwordEncoder.matches("rawPassword", "encodedPassword")).thenReturn(true);

            // when
            MemberModel member = new MemberModel(loginId, encodedPassword, name, birthDate, email);

            // then
            assertAll(
                () -> assertThat(member.loginId()).isEqualTo(loginId),
                () -> assertThat(member.matchesPassword("rawPassword", passwordEncoder)).isTrue(),
                () -> assertThat(member.name()).isEqualTo(name),
                () -> assertThat(member.birthDate()).isEqualTo(birthDate),
                () -> assertThat(member.email()).isEqualTo(email)
            );
        }

        @DisplayName("email이 null이어도 생성할 수 있다")
        @Test
        void createsWithNullEmail() {
            // given
            LoginId loginId = new LoginId("kwonmo");
            String encodedPassword = "encodedPassword";
            MemberName name = new MemberName("양권모");
            LocalDate birthDate = LocalDate.of(1998, 9, 16);

            // when
            MemberModel member = new MemberModel(loginId, encodedPassword, name, birthDate, null);

            // then
            assertThat(member.email()).isNull();
        }
    }

    @DisplayName("비밀번호 변경")
    @Nested
    class ChangePassword {

        @DisplayName("새 비밀번호로 변경하면 이전 비밀번호는 매칭되지 않는다")
        @Test
        void newPasswordReplacesOld() {
            // given
            MemberModel member = new MemberModel(
                new LoginId("kwonmo"), "oldEncodedPassword", new MemberName("양권모"),
                LocalDate.of(1998, 9, 16), new Email("kwonmo@example.com")
            );
            String newEncodedPassword = "newEncodedPassword";
            when(passwordEncoder.matches("newRaw", "newEncodedPassword")).thenReturn(true);
            when(passwordEncoder.matches("oldRaw", "newEncodedPassword")).thenReturn(false);

            // when
            member.changePassword(newEncodedPassword);

            // then
            assertThat(member.matchesPassword("newRaw", passwordEncoder)).isTrue();
            assertThat(member.matchesPassword("oldRaw", passwordEncoder)).isFalse();
        }
    }
}
