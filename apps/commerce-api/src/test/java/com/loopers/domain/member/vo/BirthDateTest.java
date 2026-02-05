package com.loopers.domain.member.vo;

import com.loopers.support.error.CoreException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BirthDateTest {

    @DisplayName("yyyy-MM-dd 형식의 문자열로 BirthDate를 생성할 수 있다")
    @Test
    void from_withValidFormat_succeeds() {
        BirthDate birthDate = BirthDate.from("1990-01-15");
        assertThat(birthDate.value()).isEqualTo(LocalDate.of(1990, 1, 15));
    }

    @DisplayName("생년월일이 yyyy-MM-dd 형식에 맞지 않으면 User 객체 생성에 실패한다")
    @Test
    void from_withInvalidFormat_throwsException() {
        assertThatThrownBy(() -> BirthDate.from("19900115"))
            .isInstanceOf(CoreException.class);
    }

    @DisplayName("null이면 생성에 실패한다")
    @Test
    void from_withNull_throwsException() {
        assertThatThrownBy(() -> BirthDate.from(null))
            .isInstanceOf(CoreException.class);
    }

    @DisplayName("슬래시 형식이면 생성에 실패한다")
    @Test
    void from_withSlashFormat_throwsException() {
        assertThatThrownBy(() -> BirthDate.from("1990/01/15"))
            .isInstanceOf(CoreException.class);
    }

    @DisplayName("빈 문자열이면 생성에 실패한다")
    @Test
    void from_withEmpty_throwsException() {
        assertThatThrownBy(() -> BirthDate.from(""))
            .isInstanceOf(CoreException.class);
    }
}
