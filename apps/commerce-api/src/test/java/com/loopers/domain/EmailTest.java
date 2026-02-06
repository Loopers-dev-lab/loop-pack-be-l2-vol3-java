package com.loopers.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.loopers.support.error.CoreException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class EmailTest {

    @DisplayName("이메일 객체를 생성할 때, ")
    @Nested
    class Create {

        @DisplayName("올바른 이메일 형식이 주어지면, 정상적으로 생성된다.")
        @ParameterizedTest
        @ValueSource(strings = {
                "test@example.com",
                "user.name+tag@domain.co.kr",
                "12345@loopers.io",
                "email@sub.domain.com"
        })
        void createEmail_whenValidFormat(String validEmail) {
            // act
            Email email = new Email(validEmail);

            // assert
            assertThat(email.getMail()).isEqualTo(validEmail);
        }

        @DisplayName("이메일이 null이거나 비어있으면 예외가 발생한다.")
        @ParameterizedTest
        @ValueSource(strings = {"", " ", "   "})
        void createEmail_whenNullOrBlank(String blankEmail) {
            // null 케이스 별도 테스트
            assertThatThrownBy(() -> new Email(null))
                    .isInstanceOf(CoreException.class)
                    .hasMessageContaining("이메일은 비어있을 수 없습니다.");

            // 공백 케이스
            assertThatThrownBy(() -> new Email(blankEmail))
                    .isInstanceOf(CoreException.class)
                    .hasMessageContaining("이메일은 비어있을 수 없습니다.");
        }

        @DisplayName("형식이 올바르지 않으면 예외가 발생한다.")
        @ParameterizedTest
        @ValueSource(strings = {
                "plainaddress",      // @ 없음
                "#@%^%#$@#$@#.com",  // 특수문자 남발
                "@domain.com",       // 로컬 파트 없음
                "Joe Smith <email@domain.com>", // 이름 포함
                "email.domain.com",  // @ 없음
                "email@domain@domain.com", // @ 중복
                "email@domain..com"  // 도메인 마침표 중복
        })
        void createEmail_whenInvalidFormat(String invalidEmail) {
            assertThatThrownBy(() -> new Email(invalidEmail))
                    .isInstanceOf(CoreException.class)
                    .hasMessageContaining("이메일 형식이 올바르지 않습니다.");
        }
    }
}
