package com.loopers.domain.user;

import com.loopers.domain.user.vo.BirthDate;
import com.loopers.domain.user.vo.Email;
import com.loopers.domain.user.vo.LoginId;
import com.loopers.domain.user.vo.UserName;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

/**
 * User Entity 단위 테스트
 */
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
public class UserTest {

    @DisplayName("User를 생성할 때,")
    @Nested
    class Create {

        @Test
        void 유효한_정보를_전달하면_정상적으로_생성된다() {
            // arrange
            LoginId loginId = new LoginId("nahyeon");
            String encodedPassword = "$2a$10$encodedPasswordHash";
            UserName name = new UserName("홍길동");
            BirthDate birthDate = new BirthDate("1994-11-15");
            Email email = new Email("nahyeon@example.com");

            // act
            User user = User.create(loginId, encodedPassword, name, birthDate, email);

            // assert
            assertAll(
                    () -> assertThat(user.getLoginId().getValue()).isEqualTo("nahyeon"),
                    () -> assertThat(user.getPassword()).isEqualTo(encodedPassword),
                    () -> assertThat(user.getName().getValue()).isEqualTo("홍길동"),
                    () -> assertThat(user.getBirthDate().getValue()).isEqualTo(birthDate.getValue()),
                    () -> assertThat(user.getEmail().getValue()).isEqualTo("nahyeon@example.com")
            );
        }
    }

    @DisplayName("비밀번호를 변경할 때,")
    @Nested
    class ChangePassword {

        @Test
        void 새_인코딩된_비밀번호로_변경된다() {
            // arrange
            User user = User.create(
                    new LoginId("nahyeon"),
                    "$2a$10$oldHash",
                    new UserName("홍길동"),
                    new BirthDate("1994-11-15"),
                    new Email("nahyeon@example.com")
            );
            String newEncodedPassword = "$2a$10$newHash";

            // act
            user.changePassword(newEncodedPassword);

            // assert
            assertThat(user.getPassword()).isEqualTo(newEncodedPassword);
        }
    }
}
