package com.loopers.domain.member;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest
class MemberServiceTest {

    private final MemberService memberService;
    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;
    private final DatabaseCleanUp databaseCleanUp;

    @Autowired
    public MemberServiceTest(
        MemberService memberService,
        MemberRepository memberRepository,
        PasswordEncoder passwordEncoder,
        DatabaseCleanUp databaseCleanUp
    ) {
        this.memberService = memberService;
        this.memberRepository = memberRepository;
        this.passwordEncoder = passwordEncoder;
        this.databaseCleanUp = databaseCleanUp;
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("회원가입을 할 때,")
    @Nested
    class SignUp {

        @DisplayName("정상적인 정보로 가입하면, 회원이 저장되고 비밀번호가 암호화된다.")
        @Test
        void signUp_withValidInfo_savesMemberWithEncryptedPassword() {
            // arrange
            String loginId = "testuser1";
            String rawPassword = "Test1234!";
            String name = "홍길동";
            LocalDate birthday = LocalDate.of(1995, 3, 15);
            String email = "test@example.com";

            // act
            Member savedMember = memberService.signUp(loginId, rawPassword, name, birthday, email);

            // assert
            assertAll(
                () -> assertThat(savedMember.getId()).isNotNull(),
                () -> assertThat(savedMember.getLoginId()).isEqualTo(loginId),
                () -> assertThat(savedMember.getName()).isEqualTo(name),
                () -> assertThat(savedMember.getBirthday()).isEqualTo(birthday),
                () -> assertThat(savedMember.getEmail()).isEqualTo(email),
                () -> assertThat(savedMember.getPassword()).isNotEqualTo(rawPassword),
                () -> assertThat(passwordEncoder.matches(rawPassword, savedMember.getPassword())).isTrue()
            );
        }

        @DisplayName("이미 존재하는 loginId로 가입하면, CONFLICT 예외가 발생한다.")
        @Test
        void signUp_withDuplicateLoginId_throwsConflict() {
            // arrange
            Member existing = new Member("testuser1", "Test1234!", "홍길동", LocalDate.of(1995, 3, 15), "test@example.com");
            existing.encryptPassword(passwordEncoder.encode("Test1234!"));
            memberRepository.save(existing);

            // act & assert
            assertThatThrownBy(() -> memberService.signUp("testuser1", "Other1234!", "김철수", LocalDate.of(1990, 1, 1), "other@example.com"))
                .isInstanceOf(CoreException.class)
                .satisfies(ex -> assertThat(((CoreException) ex).getErrorType()).isEqualTo(ErrorType.CONFLICT));
        }

        @DisplayName("이미 존재하는 email로 가입하면, CONFLICT 예외가 발생한다.")
        @Test
        void signUp_withDuplicateEmail_throwsConflict() {
            // arrange
            Member existing = new Member("testuser1", "Test1234!", "홍길동", LocalDate.of(1995, 3, 15), "test@example.com");
            existing.encryptPassword(passwordEncoder.encode("Test1234!"));
            memberRepository.save(existing);

            // act & assert
            assertThatThrownBy(() -> memberService.signUp("testuser2", "Other1234!", "김철수", LocalDate.of(1990, 1, 1), "test@example.com"))
                .isInstanceOf(CoreException.class)
                .satisfies(ex -> assertThat(((CoreException) ex).getErrorType()).isEqualTo(ErrorType.CONFLICT));
        }
    }

    @DisplayName("인증을 할 때,")
    @Nested
    class Authenticate {

        @DisplayName("올바른 loginId와 password로 인증하면, 회원을 반환한다.")
        @Test
        void authenticate_withValidCredentials_returnsMember() {
            // arrange
            String rawPassword = "Test1234!";
            Member existing = new Member("testuser1", rawPassword, "홍길동", LocalDate.of(1995, 3, 15), "test@example.com");
            existing.encryptPassword(passwordEncoder.encode(rawPassword));
            memberRepository.save(existing);

            // act
            Member result = memberService.authenticate("testuser1", rawPassword);

            // assert
            assertAll(
                () -> assertThat(result.getLoginId()).isEqualTo("testuser1"),
                () -> assertThat(result.getName()).isEqualTo("홍길동"),
                () -> assertThat(result.getEmail()).isEqualTo("test@example.com")
            );
        }

        @DisplayName("존재하지 않는 loginId로 인증하면, UNAUTHORIZED 예외가 발생한다.")
        @Test
        void authenticate_withNonExistentLoginId_throwsUnauthorized() {
            // act & assert
            assertThatThrownBy(() -> memberService.authenticate("nonexistent", "Test1234!"))
                .isInstanceOf(CoreException.class)
                .satisfies(ex -> assertThat(((CoreException) ex).getErrorType()).isEqualTo(ErrorType.UNAUTHORIZED));
        }

        @DisplayName("비밀번호가 일치하지 않으면, UNAUTHORIZED 예외가 발생한다.")
        @Test
        void authenticate_withWrongPassword_throwsUnauthorized() {
            // arrange
            Member existing = new Member("testuser1", "Test1234!", "홍길동", LocalDate.of(1995, 3, 15), "test@example.com");
            existing.encryptPassword(passwordEncoder.encode("Test1234!"));
            memberRepository.save(existing);

            // act & assert
            assertThatThrownBy(() -> memberService.authenticate("testuser1", "Wrong1234!"))
                .isInstanceOf(CoreException.class)
                .satisfies(ex -> assertThat(((CoreException) ex).getErrorType()).isEqualTo(ErrorType.UNAUTHORIZED));
        }
    }
}