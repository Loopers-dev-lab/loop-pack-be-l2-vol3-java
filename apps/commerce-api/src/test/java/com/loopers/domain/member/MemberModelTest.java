package com.loopers.domain.member;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * MemberModel 도메인 엔티티 테스트
 *
 * 수정사항:
 * - IllegalArgumentException → CoreException으로 변경
 * - ErrorType 검증 추가
 */
@DisplayName("MemberModel 도메인 엔티티 테스트")
class MemberModelTest {

    // ========================================
    // 1. 정상 케이스 - Happy Path
    // ========================================

    @Test
    @DisplayName("유효한 입력으로 MemberModel 생성 성공")
    void createMemberModel_WithValidInputs_ShouldSuccess() {
        // Given: 유효한 회원 정보 (암호화된 비밀번호 사용)
        String loginId = "testuser123";
        String encodedPassword = "$2a$10$dummyEncodedPasswordHash";
        String name = "홍길동";
        LocalDate birthDate = LocalDate.of(1990, 1, 1);
        String email = "test@example.com";

        // When: MemberModel 생성
        MemberModel member = MemberModel.createWithEncodedPassword(
                loginId,
                encodedPassword,
                name,
                birthDate,
                email
        );

        // Then: 생성된 객체의 값 검증
        assertThat(member).isNotNull();
        assertThat(member.getLoginId()).isEqualTo(loginId);
        assertThat(member.getName()).isEqualTo(name);
        assertThat(member.getBirthDate()).isEqualTo(birthDate);
        assertThat(member.getEmail()).isEqualTo(email);
        assertThat(member.getLoginPw()).isEqualTo(encodedPassword);
    }

    // ========================================
    // 2. 로그인 ID 검증 테스트
    // ========================================

    @Test
    @DisplayName("로그인 ID가 영문+숫자가 아니면 CoreException 발생")
    void createMemberModel_WithInvalidLoginId_ShouldThrowCoreException() {
        // Given: 특수문자가 포함된 로그인 ID
        String invalidLoginId = "test@user";

        // When & Then: CoreException 발생
        assertThatThrownBy(() -> MemberModel.createWithEncodedPassword(
                invalidLoginId,
                "$2a$10$dummyHash",
                "홍길동",
                LocalDate.of(1990, 1, 1),
                "test@example.com"
        ))
                .isInstanceOf(CoreException.class)
                .satisfies(ex -> {
                    CoreException coreEx = (CoreException) ex;
                    assertThat(coreEx.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
                    assertThat(coreEx.getCustomMessage()).contains("영문과 숫자만");
                });
    }

    @Test
    @DisplayName("로그인 ID에 한글이 포함되면 CoreException 발생")
    void createMemberModel_WithKoreanInLoginId_ShouldThrowCoreException() {
        // Given: 한글이 포함된 로그인 ID
        String invalidLoginId = "test홍길동";

        // When & Then: CoreException 발생
        assertThatThrownBy(() -> MemberModel.createWithEncodedPassword(
                invalidLoginId,
                "$2a$10$dummyHash",
                "홍길동",
                LocalDate.of(1990, 1, 1),
                "test@example.com"
        ))
                .isInstanceOf(CoreException.class)
                .satisfies(ex -> {
                    CoreException coreEx = (CoreException) ex;
                    assertThat(coreEx.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
                });
    }

    // ========================================
    // 3. 비밀번호 검증 테스트 (validatePassword 메서드)
    // ========================================

    @Test
    @DisplayName("비밀번호가 8자 미만이면 CoreException 발생 (INVALID_PASSWORD)")
    void validatePassword_WithShortPassword_ShouldThrowCoreException() {
        // Given: 7자 비밀번호
        String shortPassword = "Test1!";
        LocalDate birthDate = LocalDate.of(1990, 1, 1);

        // When & Then: CoreException 발생
        assertThatThrownBy(() -> MemberModel.validatePassword(shortPassword, birthDate))
                .isInstanceOf(CoreException.class)
                .satisfies(ex -> {
                    CoreException coreEx = (CoreException) ex;
                    assertThat(coreEx.getErrorType()).isEqualTo(ErrorType.INVALID_PASSWORD);
                    assertThat(coreEx.getCustomMessage()).contains("8~16자");
                });
    }

    @Test
    @DisplayName("비밀번호가 16자 초과하면 CoreException 발생 (INVALID_PASSWORD)")
    void validatePassword_WithLongPassword_ShouldThrowCoreException() {
        // Given: 17자 비밀번호
        String longPassword = "Test1234!@#$%^&*(";
        LocalDate birthDate = LocalDate.of(1990, 1, 1);

        // When & Then: CoreException 발생
        assertThatThrownBy(() -> MemberModel.validatePassword(longPassword, birthDate))
                .isInstanceOf(CoreException.class)
                .satisfies(ex -> {
                    CoreException coreEx = (CoreException) ex;
                    assertThat(coreEx.getErrorType()).isEqualTo(ErrorType.INVALID_PASSWORD);
                });
    }

    @Test
    @DisplayName("비밀번호에 허용되지 않은 문자 포함 시 CoreException 발생")
    void validatePassword_WithInvalidCharInPassword_ShouldThrowCoreException() {
        // Given: 한글이 포함된 비밀번호
        String invalidPassword = "Test1234한글";
        LocalDate birthDate = LocalDate.of(1990, 1, 1);

        // When & Then: CoreException 발생
        assertThatThrownBy(() -> MemberModel.validatePassword(invalidPassword, birthDate))
                .isInstanceOf(CoreException.class)
                .satisfies(ex -> {
                    CoreException coreEx = (CoreException) ex;
                    assertThat(coreEx.getErrorType()).isEqualTo(ErrorType.INVALID_PASSWORD);
                    assertThat(coreEx.getCustomMessage()).contains("영문 대소문자, 숫자, 특수문자만");
                });
    }

    @Test
    @DisplayName("비밀번호에 생년월일 포함 시 CoreException 발생 (INVALID_PASSWORD)")
    void validatePassword_WithBirthDateInPassword_ShouldThrowCoreException() {
        // Given: 생년월일(19900101)이 포함된 비밀번호
        String passwordWithBirthDate = "Test19900101!";
        LocalDate birthDate = LocalDate.of(1990, 1, 1);

        // When & Then: CoreException 발생
        assertThatThrownBy(() -> MemberModel.validatePassword(passwordWithBirthDate, birthDate))
                .isInstanceOf(CoreException.class)
                .satisfies(ex -> {
                    CoreException coreEx = (CoreException) ex;
                    assertThat(coreEx.getErrorType()).isEqualTo(ErrorType.INVALID_PASSWORD);
                    assertThat(coreEx.getCustomMessage()).contains("생년월일");
                });
    }

    @Test
    @DisplayName("유효한 비밀번호는 검증 통과")
    void validatePassword_WithValidPassword_ShouldPass() {
        // Given: 유효한 비밀번호
        String validPassword = "Test1234!@#";
        LocalDate birthDate = LocalDate.of(1990, 1, 1);

        // When & Then: 예외 발생하지 않음
        MemberModel.validatePassword(validPassword, birthDate);
    }

    // ========================================
    // 4. 이메일 포맷 검증 테스트
    // ========================================

    @Test
    @DisplayName("이메일 형식이 유효하지 않으면 CoreException 발생")
    void createMemberModel_WithInvalidEmailFormat_ShouldThrowCoreException() {
        // Given: 잘못된 이메일 형식
        String invalidEmail = "test@invalid";

        // When & Then: CoreException 발생
        assertThatThrownBy(() -> MemberModel.createWithEncodedPassword(
                "testuser123",
                "$2a$10$dummyHash",
                "홍길동",
                LocalDate.of(1990, 1, 1),
                invalidEmail
        ))
                .isInstanceOf(CoreException.class)
                .satisfies(ex -> {
                    CoreException coreEx = (CoreException) ex;
                    assertThat(coreEx.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
                    assertThat(coreEx.getCustomMessage()).contains("이메일");
                });
    }

    // ========================================
    // 5. 이름 검증 테스트
    // ========================================

    @Test
    @DisplayName("이름이 비어있으면 CoreException 발생")
    void createMemberModel_WithEmptyName_ShouldThrowCoreException() {
        // Given: 빈 이름
        String emptyName = "";

        // When & Then: CoreException 발생
        assertThatThrownBy(() -> MemberModel.createWithEncodedPassword(
                "testuser123",
                "$2a$10$dummyHash",
                emptyName,
                LocalDate.of(1990, 1, 1),
                "test@example.com"
        ))
                .isInstanceOf(CoreException.class)
                .satisfies(ex -> {
                    CoreException coreEx = (CoreException) ex;
                    assertThat(coreEx.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
                    assertThat(coreEx.getCustomMessage()).contains("이름");
                });
    }

    // ========================================
    // 6. 생년월일 검증 테스트
    // ========================================

    @Test
    @DisplayName("생년월일이 null이면 CoreException 발생")
    void createMemberModel_WithNullBirthDate_ShouldThrowCoreException() {
        // Given: null 생년월일
        LocalDate nullBirthDate = null;

        // When & Then: CoreException 발생
        assertThatThrownBy(() -> MemberModel.createWithEncodedPassword(
                "testuser123",
                "$2a$10$dummyHash",
                "홍길동",
                nullBirthDate,
                "test@example.com"
        ))
                .isInstanceOf(CoreException.class)
                .satisfies(ex -> {
                    CoreException coreEx = (CoreException) ex;
                    assertThat(coreEx.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
                    assertThat(coreEx.getCustomMessage()).contains("생년월일");
                });
    }

    @Test
    @DisplayName("생년월일이 미래 날짜면 CoreException 발생")
    void createMemberModel_WithFutureBirthDate_ShouldThrowCoreException() {
        // Given: 미래 날짜
        LocalDate futureBirthDate = LocalDate.now().plusDays(1);

        // When & Then: CoreException 발생
        assertThatThrownBy(() -> MemberModel.createWithEncodedPassword(
                "testuser123",
                "$2a$10$dummyHash",
                "홍길동",
                futureBirthDate,
                "test@example.com"
        ))
                .isInstanceOf(CoreException.class)
                .satisfies(ex -> {
                    CoreException coreEx = (CoreException) ex;
                    assertThat(coreEx.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
                });
    }

    // ========================================
    // 7. 이름 마스킹 테스트
    // ========================================

    @Test
    @DisplayName("이름 마지막 글자 마스킹 - 홍길동 → 홍길*")
    void getMaskedName_WithThreeCharacterName_ShouldMaskLastCharacter() {
        // Given: 3글자 이름을 가진 회원
        MemberModel member = MemberModel.createWithEncodedPassword(
                "testuser123",
                "$2a$10$dummyHash",
                "홍길동",
                LocalDate.of(1990, 1, 1),
                "test@example.com"
        );

        // When: 마스킹된 이름 조회
        String maskedName = member.getMaskedName();

        // Then: 마지막 글자가 *로 치환됨
        assertThat(maskedName).isEqualTo("홍길*");
    }

    @Test
    @DisplayName("한 글자 이름 전체 마스킹 - A → *")
    void getMaskedName_WithOneCharacterName_ShouldMaskCompletely() {
        // Given: 1글자 이름을 가진 회원
        MemberModel member = MemberModel.createWithEncodedPassword(
                "testuser123",
                "$2a$10$dummyHash",
                "A",
                LocalDate.of(1990, 1, 1),
                "test@example.com"
        );

        // When: 마스킹된 이름 조회
        String maskedName = member.getMaskedName();

        // Then: 전체가 *로 치환됨
        assertThat(maskedName).isEqualTo("*");
    }

    @Test
    @DisplayName("두 글자 이름 마스킹 - 홍길 → 홍*")
    void getMaskedName_WithTwoCharacterName_ShouldMaskLastCharacter() {
        // Given: 2글자 이름을 가진 회원
        MemberModel member = MemberModel.createWithEncodedPassword(
                "testuser123",
                "$2a$10$dummyHash",
                "홍길",
                LocalDate.of(1990, 1, 1),
                "test@example.com"
        );

        // When: 마스킹된 이름 조회
        String maskedName = member.getMaskedName();

        // Then: 마지막 글자가 *로 치환됨
        assertThat(maskedName).isEqualTo("홍*");
    }

    // ========================================
    // 8. 비밀번호 업데이트 테스트
    // ========================================

    @Test
    @DisplayName("유효한 암호화된 비밀번호로 업데이트 성공")
    void updatePassword_WithValidEncodedPassword_ShouldSuccess() {
        // Given: 회원 생성
        MemberModel member = MemberModel.createWithEncodedPassword(
                "testuser123",
                "$2a$10$oldHash",
                "홍길동",
                LocalDate.of(1990, 1, 1),
                "test@example.com"
        );

        // When: 비밀번호 업데이트
        String newEncodedPassword = "$2a$10$newHash";
        member.updatePassword(newEncodedPassword);

        // Then: 비밀번호가 변경됨
        assertThat(member.getLoginPw()).isEqualTo(newEncodedPassword);
    }

    @Test
    @DisplayName("null 또는 빈 암호화 비밀번호로 업데이트 시 CoreException 발생")
    void updatePassword_WithNullOrEmptyPassword_ShouldThrowCoreException() {
        // Given: 회원 생성
        MemberModel member = MemberModel.createWithEncodedPassword(
                "testuser123",
                "$2a$10$oldHash",
                "홍길동",
                LocalDate.of(1990, 1, 1),
                "test@example.com"
        );

        // When & Then: null로 업데이트 시도
        assertThatThrownBy(() -> member.updatePassword(null))
                .isInstanceOf(CoreException.class)
                .satisfies(ex -> {
                    CoreException coreEx = (CoreException) ex;
                    assertThat(coreEx.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
                    assertThat(coreEx.getCustomMessage()).contains("암호화된 비밀번호");
                });

        // When & Then: 빈 문자열로 업데이트 시도
        assertThatThrownBy(() -> member.updatePassword(""))
                .isInstanceOf(CoreException.class)
                .satisfies(ex -> {
                    CoreException coreEx = (CoreException) ex;
                    assertThat(coreEx.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
                });
    }
}
