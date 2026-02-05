package com.loopers.domain.user;

import com.loopers.domain.user.exception.UserValidationException;
import com.loopers.domain.user.vo.BirthDate;
import com.loopers.domain.user.vo.Email;
import com.loopers.domain.user.vo.Name;
import com.loopers.domain.user.vo.Password;
import com.loopers.domain.user.vo.UserId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

public class UserTest {

    @Nested
    @DisplayName("User 생성")
    class UserCreationTest {

        @Test
        @DisplayName("User 생성 성공")
        void createUserSuccess() {
            // given
            UserId userId = mock(UserId.class);
            Password password = mock(Password.class);
            Name name = mock(Name.class);
            Email email = mock(Email.class);
            BirthDate birthDate = mock(BirthDate.class);

            when(password.value()).thenReturn("1Q2w3e4r!");
            when(birthDate.toYymmdd()).thenReturn("990115");
            when(birthDate.toMmdd()).thenReturn("0115");
            when(birthDate.toDdmm()).thenReturn("1501");

            // when & then
            assertThatNoException()
                    .isThrownBy(() -> new User(userId, password, name, email, birthDate));
        }
    }

    @Nested
    @DisplayName("비밀번호 생년월일 포함 검증")
    class PasswordBirthDateValidationTest {

        @Test
        @DisplayName("비밀번호에 생년월일(YYMMDD) 포함 시 실패")
        void passwordContainsYymmdd() {
            // given
            UserId userId = new UserId("testuser");
            Password password = new Password("Qw990115!");
            Name name = new Name("홍길동");
            Email email = new Email("test@example.com");
            BirthDate birthDate = BirthDate.of("19990115");

            // when & then
            assertThatThrownBy(() -> new User(userId, password, name, email, birthDate))
                    .isInstanceOf(UserValidationException.class)
                    .hasMessage("비밀번호에 생년월일을 포함할 수 없습니다");
        }

        @Test
        @DisplayName("비밀번호에 생년월일(MMDD) 포함 시 실패")
        void passwordContainsMmdd() {
            // given
            UserId userId = new UserId("testuser");
            Password password = new Password("1Qwer0115!");
            Name name = new Name("홍길동");
            Email email = new Email("test@example.com");
            BirthDate birthDate = BirthDate.of("19990115");

            // when & then
            assertThatThrownBy(() -> new User(userId, password, name, email, birthDate))
                    .isInstanceOf(UserValidationException.class)
                    .hasMessage("비밀번호에 생년월일을 포함할 수 없습니다");
        }

        @Test
        @DisplayName("비밀번호에 생년월일(DDMM) 포함 시 실패")
        void passwordContainsDdmm() {
            // given
            UserId userId = new UserId("testuser");
            Password password = new Password("1Qwer1501!");
            Name name = new Name("홍길동");
            Email email = new Email("test@example.com");
            BirthDate birthDate = BirthDate.of("19990115");

            // when & then
            assertThatThrownBy(() -> new User(userId, password, name, email, birthDate))
                    .isInstanceOf(UserValidationException.class)
                    .hasMessage("비밀번호에 생년월일을 포함할 수 없습니다");
        }

        @Test
        @DisplayName("비밀번호에 생년월일 미포함 시 성공")
        void passwordNotContainsBirthDate() {
            // given
            UserId userId = new UserId("testuser");
            Password password = new Password("1Q2w3e4r!");
            Name name = new Name("홍길동");
            Email email = new Email("test@example.com");
            BirthDate birthDate = BirthDate.of("19990115");

            // when & then
            assertThatNoException()
                    .isThrownBy(() -> new User(userId, password, name, email, birthDate));
        }
    }

    @Nested
    @DisplayName("이름 마스킹")
    class MaskingTest {

        @Test
        @DisplayName("한글 이름 마스킹 - 3글자")
        void getMaskedKoreanName() {
            // given
            UserId userId = new UserId("testuser");
            Password password = new Password("1Q2w3e4r!");
            Name name = new Name("홍길동");
            Email email = new Email("test@example.com");
            BirthDate birthDate = new BirthDate(LocalDate.of(1999, 1, 15));

            User user = new User(userId, password, name, email, birthDate);

            // when
            String maskedName = user.getMaskedName();

            // then
            assertThat(maskedName).isEqualTo("홍길*");
        }

        @Test
        @DisplayName("영문 이름 마스킹")
        void getMaskedEnglishName() {
            // given
            UserId userId = new UserId("testuser");
            Password password = new Password("1Q2w3e4r!");
            Name name = new Name("John");
            Email email = new Email("test@example.com");
            BirthDate birthDate = new BirthDate(LocalDate.of(1999, 1, 15));

            User user = new User(userId, password, name, email, birthDate);

            // when
            String maskedName = user.getMaskedName();

            // then
            assertThat(maskedName).isEqualTo("Joh*");
        }

        @Test
        @DisplayName("한 글자 이름 마스킹")
        void getMaskedSingleCharName() {
            // given
            UserId userId = new UserId("testuser");
            Password password = new Password("1Q2w3e4r!");
            Name name = new Name("김");
            Email email = new Email("test@example.com");
            BirthDate birthDate = new BirthDate(LocalDate.of(1999, 1, 15));

            User user = new User(userId, password, name, email, birthDate);

            // when
            String maskedName = user.getMaskedName();

            // then
            assertThat(maskedName).isEqualTo("*");
        }
    }
}
