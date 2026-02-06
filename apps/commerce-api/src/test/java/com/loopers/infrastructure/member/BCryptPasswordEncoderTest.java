package com.loopers.infrastructure.member;

import com.loopers.domain.member.PasswordEncoder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * BCryptPasswordEncoder 테스트
 *
 * TDD Red Phase: 실패하는 테스트를 먼저 작성
 * 테스트 대상: apps/commerce-api/src/main/java/com/loopers/infrastructure/member/BCryptPasswordEncoder.java
 */
@DisplayName("BCryptPasswordEncoder 테스트")
public class BCryptPasswordEncoderTest {
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void setUp(){
        passwordEncoder = new BCryptPasswordEncoder();
    }

    // ========================================
    // 1. 비밀번호 암호화 테스트
    // ========================================

    @Test
    @DisplayName("비밀번호 암호화 성공")
    void encode_WithRawPassword_ShouldReturnEncodedPassword() {
        //Given: 평문 비밀번호
        String rawPassword = "Test1234!@#";

        //When : 암호화
        String encodedPassword = passwordEncoder.encode(rawPassword);

        //Then : 암호화된 비밀번호 반환
        assertThat(encodedPassword).isNotNull();
        assertThat(encodedPassword).isNotEmpty();
        assertThat(encodedPassword).isNotEqualTo(rawPassword);  // 원본과 다름
        assertThat(encodedPassword).startsWith("$2a$");  // BCrypt 형식
    }

    @Test
    @DisplayName("동일한 비밀번호를 두 번 암호화하면 다른 결과")
    void encode_SamePasswordTwice_ShouldReturnDifferentResults(){
        //Given : 동일한 평문 비밀번호
        String rawPassword = "Test1234!@#";

        // When: 두 번 암호화
        String encoded1 = passwordEncoder.encode(rawPassword);
        String encoded2 = passwordEncoder.encode(rawPassword);

        // Then: 결과가 다름 (BCrypt의 salt 때문)
        assertThat(encoded1).isNotEqualTo(encoded2);
    }

    // ========================================
    // 2. 비밀번호 검증 테스트
    // ========================================
    @Test
    @DisplayName("올바른 비밀번호 검증 성공")
    void matches_WithCorrectPassword_ShouldReturnTrue() {
        // Given: 암호화된 비밀번호
        String rawPassword = "Test1234!@#";
        String encodedPassword = passwordEncoder.encode(rawPassword);

        // When: 원본 비밀번호로 검증
        boolean matches = passwordEncoder.matches(rawPassword, encodedPassword);

        // Then: 일치함
        assertThat(matches).isTrue();
    }

    @Test
    @DisplayName("잘못된 비밀번호 검증 실패")
    void matches_WithWrongPassword_ShouldReturnFalse() {
        // Given: 암호화된 비밀번호
        String rawPassword = "Test1234!@#";
        String encodedPassword = passwordEncoder.encode(rawPassword);

        // When: 다른 비밀번호로 검증
        String wrongPassword = "WrongPass123!";
        boolean matches = passwordEncoder.matches(wrongPassword, encodedPassword);

        // Then: 불일치
        assertThat(matches).isFalse();
    }

    @Test
    @DisplayName("대소문자를 구분하여 검증")
    void matches_WithDifferentCase_ShouldReturnFalse() {
        // Given: 암호화된 비밀번호
        String rawPassword = "Test1234!@#";
        String encodedPassword = passwordEncoder.encode(rawPassword);

        // When: 대소문자가 다른 비밀번호로 검증
        String differentCasePassword = "test1234!@#";
        boolean matches = passwordEncoder.matches(differentCasePassword, encodedPassword);

        // Then: 불일치
        assertThat(matches).isFalse();
    }

    // ========================================
    // 3. 엣지 케이스 테스트
    // ========================================

    @Test
    @DisplayName("빈 문자열 암호화")
    void encode_WithEmptyString_ShouldReturnEncodedPassword() {
        // Given: 빈 문자열
        String emptyPassword = "";

        // When: 암호화
        String encodedPassword = passwordEncoder.encode(emptyPassword);

        // Then: 암호화됨 (유효성 검증은 도메인 레이어에서)
        assertThat(encodedPassword).isNotNull();
        assertThat(encodedPassword).isNotEmpty();
    }

    @Test
    @DisplayName("특수문자가 많은 비밀번호 암호화")
    void encode_WithSpecialCharacters_ShouldReturnEncodedPassword() {
        // Given: 특수문자가 많은 비밀번호
        String complexPassword = "!@#$%^&*()_+-=[]{}|;':\",./<>?";

        // When: 암호화
        String encodedPassword = passwordEncoder.encode(complexPassword);

        // Then: 정상적으로 암호화됨
        assertThat(encodedPassword).isNotNull();

        // 검증도 성공
        assertThat(passwordEncoder.matches(complexPassword, encodedPassword)).isTrue();
    }

    @Test
    @DisplayName("매우 긴 비밀번호 암호화")
    void encode_WithVeryLongPassword_ShouldReturnEncodedPassword() {
        // Given: 72자 비밀번호 (BCrypt 제한)
        String longPassword = "A".repeat(72);

        // When: 암호화
        String encodedPassword = passwordEncoder.encode(longPassword);

        // Then: 정상적으로 암호화됨
        assertThat(encodedPassword).isNotNull();
        assertThat(passwordEncoder.matches(longPassword, encodedPassword)).isTrue();
    }

    // ========================================
    // 4. 성능 및 보안 테스트
    // ========================================


    @Test
    @DisplayName("암호화 성능 테스트 - 1초 이내 완료")
    void encode_PerformanceTest_ShouldCompleteWithinOneSecond() {
        // Given: 평문 비밀번호
        String rawPassword = "Test1234!@#";

        // When: 시간 측정
        long startTime = System.currentTimeMillis();
        String encodedPassword = passwordEncoder.encode(rawPassword);
        long endTime = System.currentTimeMillis();

        // Then: 1초 이내 완료
        long duration = endTime - startTime;
        assertThat(duration).isLessThan(1000);
        assertThat(encodedPassword).isNotNull();
    }
}
