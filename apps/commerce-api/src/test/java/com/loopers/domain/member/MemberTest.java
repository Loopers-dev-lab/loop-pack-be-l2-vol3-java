package com.loopers.domain.member;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertAll;

class MemberTest {
    
    @DisplayName("회원을 생성할 때")
    @Nested
    class Create {
        
        @DisplayName("유효한 정보가 주어지면 성공한다")
        @Test
        void success() {
            String loginId = "testuser123";
            String password = "password123";
            String name = "홍길동";
            String birthDate = "1990-01-01";
            String email = "test@example.com";
            
            Member member = new Member(loginId, password, name, birthDate, email);
            
            assertAll(
                () -> assertThat(member.getLoginId()).isEqualTo(loginId),
                () -> assertThat(member.getName()).isEqualTo(name),
                () -> assertThat(member.getBirthDate()).isEqualTo(birthDate),
                () -> assertThat(member.getEmail()).isEqualTo(email)
            );
        }
        
        @DisplayName("로그인ID가 null이면 예외가 발생한다")
        @Test
        void failsWhenLoginIdIsNull() {
            assertThatThrownBy(() -> 
                new Member(null, "pw", "name", "1990-01-01", "email@test.com")
            )
            .isInstanceOf(CoreException.class)
            .hasFieldOrPropertyWithValue("errorType", ErrorType.BAD_REQUEST);
        }
        
        @DisplayName("로그인ID가 빈 문자열이면 예외가 발생한다")
        @Test
        void failsWhenLoginIdIsBlank() {
            assertThatThrownBy(() -> 
                new Member("  ", "pw", "name", "1990-01-01", "email@test.com")
            )
            .isInstanceOf(CoreException.class);
        }
        
        @DisplayName("비밀번호가 null이면 예외가 발생한다")
        @Test
        void failsWhenPasswordIsNull() {
            assertThatThrownBy(() -> 
                new Member("loginId", null, "name", "1990-01-01", "email@test.com")
            )
            .isInstanceOf(CoreException.class);
        }
        
        @DisplayName("이름이 null이면 예외가 발생한다")
        @Test
        void failsWhenNameIsNull() {
            assertThatThrownBy(() -> 
                new Member("loginId", "pw", null, "1990-01-01", "email@test.com")
            )
            .isInstanceOf(CoreException.class);
        }
        
        @DisplayName("생년월일이 null이면 예외가 발생한다")
        @Test
        void failsWhenBirthDateIsNull() {
            assertThatThrownBy(() -> 
                new Member("loginId", "pw", "name", null, "email@test.com")
            )
            .isInstanceOf(CoreException.class);
        }
        
        @DisplayName("이메일이 null이면 예외가 발생한다")
        @Test
        void failsWhenEmailIsNull() {
            assertThatThrownBy(() -> 
                new Member("loginId", "pw", "name", "1990-01-01", null)
            )
            .isInstanceOf(CoreException.class);
        }
    }
}
