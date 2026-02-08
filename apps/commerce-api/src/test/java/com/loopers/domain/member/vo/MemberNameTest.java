package com.loopers.domain.member.vo;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class MemberNameTest {

    @DisplayName("MemberName을 생성할 때, ")
    @Nested
    class Create {

        @DisplayName("유효한 이름이면 정상 생성된다")
        @Test
        void success() {
            MemberName name = assertDoesNotThrow(() -> new MemberName("홍길동"));
            assertThat(name.value()).isEqualTo("홍길동");
        }

        @DisplayName("빈 값이면 BAD_REQUEST 예외가 발생한다")
        @Test
        void throwsException_whenEmpty() {
            assertThatThrownBy(() -> new MemberName(""))
                .isInstanceOf(CoreException.class)
                .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.BAD_REQUEST));
        }

        @DisplayName("null이면 BAD_REQUEST 예외가 발생한다")
        @Test
        void throwsException_whenNull() {
            assertThatThrownBy(() -> new MemberName(null))
                .isInstanceOf(CoreException.class)
                .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.BAD_REQUEST));
        }

        @DisplayName("공백만 있으면 BAD_REQUEST 예외가 발생한다")
        @Test
        void throwsException_whenBlank() {
            assertThatThrownBy(() -> new MemberName("   "))
                .isInstanceOf(CoreException.class)
                .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.BAD_REQUEST));
        }
    }

    @DisplayName("이름 마스킹 시, ")
    @Nested
    class Masking {

        @DisplayName("마지막 글자가 *로 마스킹된다")
        @Test
        void masksLastCharacter() {
            MemberName name = new MemberName("홍길동");
            assertThat(name.masked()).isEqualTo("홍길*");
        }

        @DisplayName("한 글자 이름이면 *만 반환된다")
        @Test
        void returnsStar_whenSingleCharacter() {
            MemberName name = new MemberName("홍");
            assertThat(name.masked()).isEqualTo("*");
        }

        @DisplayName("두 글자 이름이면 첫 글자 + *로 반환된다")
        @Test
        void masksSecondCharacter_whenTwoCharacters() {
            MemberName name = new MemberName("홍길");
            assertThat(name.masked()).isEqualTo("홍*");
        }
    }
}
