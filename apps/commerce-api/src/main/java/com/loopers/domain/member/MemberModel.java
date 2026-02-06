package com.loopers.domain.member;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.regex.Pattern;

/**
 * 회원 도메인 엔티티
 *
 * 도메인 모델의 책임:
 * - 비즈니스 규칙 검증 (유효성 검증)
 * - 도메인 로직 (이름 마스킹 등)
 * - 데이터 무결성 보장
 *
 * 예외 처리:
 * - CoreException 사용 (requirements 준수)
 */
@Entity
@Table(name = "members")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MemberModel {

    // ========================================
    // 정규표현식 패턴 (상수)
    // ========================================

    private static final Pattern LOGIN_ID_PATTERN = Pattern.compile("^[a-zA-Z0-9]+$");
    private static final Pattern PASSWORD_PATTERN = Pattern.compile("^[a-zA-Z0-9!@#$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>\\/?]+$");
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");

    private static final int PASSWORD_MIN_LENGTH = 8;
    private static final int PASSWORD_MAX_LENGTH = 16;

    // ========================================
    // 필드
    // ========================================

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String loginId;

    @Column(nullable = false, length = 100)
    private String loginPw;  // 암호화된 비밀번호

    @Column(nullable = false, length = 50)
    private String name;

    @Column(nullable = false)
    private LocalDate birthDate;

    @Column(nullable = false, length = 100)
    private String email;

    // ========================================
    // 생성자 (private - 팩토리 메서드 사용 강제)
    // ========================================

    private MemberModel(String loginId, String loginPw, String name, LocalDate birthDate, String email) {
        this.loginId = loginId;
        this.loginPw = loginPw;
        this.name = name;
        this.birthDate = birthDate;
        this.email = email;
    }

    // ========================================
    // 정적 팩토리 메서드 (생성)
    // ========================================

    /**
     * 회원 생성 (이미 암호화된 비밀번호 사용)
     * 
     * 비밀번호 검증과 암호화는 서비스 레이어에서 처리:
     * 1. MemberModel.validatePassword() 로 평문 비밀번호 검증
     * 2. PasswordEncoder.encode() 로 비밀번호 암호화
     * 3. 이 메서드를 호출하여 객체 생성
     * 
     * 이렇게 함으로써:
     * - 도메인 모델은 인프라(암호화)에 의존하지 않음 (DIP 준수)
     * - 비즈니스 규칙 검증은 도메인 모델이 책임
     * - 암호화는 인프라 계층이 책임
     *
     * @param loginId 로그인 ID
     * @param encodedPassword 이미 암호화된 비밀번호
     * @param name 이름
     * @param birthDate 생년월일
     * @param email 이메일
     * @return 생성된 MemberModel
     */
    public static MemberModel createWithEncodedPassword(
            String loginId,
            String encodedPassword,
            String name,
            LocalDate birthDate,
            String email
    ) {
        // 유효성 검증
        validateLoginId(loginId);
        validateName(name);
        validateBirthDate(birthDate);
        validateEmail(email);
        
        if (encodedPassword == null || encodedPassword.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "암호화된 비밀번호는 필수입니다.");
        }

        return new MemberModel(loginId, encodedPassword, name, birthDate, email);
    }

    // ========================================
    // 유효성 검증 메서드
    // ========================================

    /**
     * 로그인 ID 검증
     * - 영문과 숫자만 허용
     */
    private static void validateLoginId(String loginId) {
        if (loginId == null || loginId.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "로그인 ID는 필수입니다.");
        }

        if (!LOGIN_ID_PATTERN.matcher(loginId).matches()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "로그인 ID는 영문과 숫자만 허용됩니다.");
        }
    }

    /**
     * 비밀번호 유효성 검증
     * 
     * 평문 비밀번호에 대한 검증 로직
     * 서비스 레이어에서 암호화 전에 호출하여 사용
     * 
     * 검증 규칙:
     * - 8~16자
     * - 영문 대소문자, 숫자, 특수문자만 허용
     * - 생년월일 포함 불가
     *
     * @param password 평문 비밀번호
     * @param birthDate 생년월일
     * @throws CoreException INVALID_PASSWORD - 비밀번호 규칙 위반 시
     */
    public static void validatePassword(String password, LocalDate birthDate) {
        if (password == null || password.isBlank()) {
            throw new CoreException(ErrorType.INVALID_PASSWORD, "비밀번호는 필수입니다.");
        }

        // 길이 검증
        if (password.length() < PASSWORD_MIN_LENGTH || password.length() > PASSWORD_MAX_LENGTH) {
            throw new CoreException(ErrorType.INVALID_PASSWORD,
                    String.format("비밀번호는 %d~%d자여야 합니다.", PASSWORD_MIN_LENGTH, PASSWORD_MAX_LENGTH)
            );
        }

        // 허용 문자 검증
        if (!PASSWORD_PATTERN.matcher(password).matches()) {
            throw new CoreException(ErrorType.INVALID_PASSWORD, 
                    "비밀번호는 영문 대소문자, 숫자, 특수문자만 허용됩니다.");
        }

        // 생년월일 포함 여부 검증
        if (birthDate != null) {
            String birthDateString = birthDate.format(DateTimeFormatter.ofPattern("yyyyMMdd"));
            if (password.contains(birthDateString)) {
                throw new CoreException(ErrorType.INVALID_PASSWORD, 
                        "생년월일은 비밀번호에 포함될 수 없습니다.");
            }
        }
    }

    /**
     * 이름 검증
     */
    private static void validateName(String name) {
        if (name == null || name.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "이름은 필수입니다.");
        }
    }

    /**
     * 생년월일 검증
     * - null 불가
     * - 미래 날짜 불가
     */
    private static void validateBirthDate(LocalDate birthDate) {
        if (birthDate == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "생년월일은 필수입니다.");
        }

        if (birthDate.isAfter(LocalDate.now())) {
            throw new CoreException(ErrorType.BAD_REQUEST, "생년월일은 미래 날짜일 수 없습니다.");
        }
    }

    /**
     * 이메일 검증
     */
    private static void validateEmail(String email) {
        if (email == null || email.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "이메일은 필수입니다.");
        }

        if (!EMAIL_PATTERN.matcher(email).matches()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "올바른 이메일 형식이 아닙니다.");
        }
    }

    // ========================================
    // 비즈니스 메서드
    // ========================================

    /**
     * 이름 마스킹
     * 마지막 글자를 *로 치환
     *
     * @return 마스킹된 이름
     */
    public String getMaskedName() {
        if (name == null || name.isEmpty()) {
            return name;
        }

        if (name.length() == 1) {
            return "*";
        }

        return name.substring(0, name.length() - 1) + "*";
    }

    /**
     * 비밀번호 업데이트 (이미 암호화된 비밀번호 사용)
     * 
     * 비밀번호 변경 로직은 서비스 레이어에서 처리:
     * 1. 현재 비밀번호 검증 (PasswordEncoder.matches)
     * 2. 새 비밀번호 != 기존 비밀번호 확인
     * 3. 새 비밀번호 유효성 검증 (MemberModel.validatePassword)
     * 4. 새 비밀번호 암호화 (PasswordEncoder.encode)
     * 5. 이 메서드를 호출하여 암호화된 비밀번호로 업데이트
     *
     * @param encodedPassword 이미 암호화된 새 비밀번호
     */
    public void updatePassword(String encodedPassword) {
        if (encodedPassword == null || encodedPassword.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "암호화된 비밀번호는 필수입니다.");
        }
        this.loginPw = encodedPassword;
    }
}
