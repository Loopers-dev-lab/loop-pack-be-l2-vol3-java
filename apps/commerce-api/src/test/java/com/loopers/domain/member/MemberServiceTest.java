package com.loopers.domain.member;

import com.loopers.support.error.CoreException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MemberServiceTest {

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private MemberService memberService;

    @DisplayName("유효한 정보로 회원가입하면 회원이 저장된다")
    @Test
    void register_withValidInfo_savesMember() {
        // arrange
        String loginId = "testuser1";
        String password = "Password1!";
        String name = "홍길동";
        LocalDate birthDate = LocalDate.of(1990, 1, 15);
        String email = "test@example.com";

        when(memberRepository.existsByLoginId(loginId)).thenReturn(false);
        when(passwordEncoder.encode(password)).thenReturn("encodedPassword");
        when(memberRepository.save(any(MemberModel.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // act
        MemberModel result = memberService.register(loginId, password, name, birthDate, email);

        // assert
        assertThat(result.getLoginId()).isEqualTo(loginId);
        assertThat(result.getName()).isEqualTo(name);
        assertThat(result.getBirthDate()).isEqualTo(birthDate);
        assertThat(result.getEmail()).isEqualTo(email);
        verify(memberRepository).save(any(MemberModel.class));
    }

    @DisplayName("이미 존재하는 로그인 ID로 가입하면 예외가 발생한다")
    @Test
    void register_withDuplicateLoginId_throwsException() {
        // arrange
        String loginId = "existinguser";
        String password = "Password1!";
        String name = "홍길동";
        LocalDate birthDate = LocalDate.of(1990, 1, 15);
        String email = "test@example.com";

        when(memberRepository.existsByLoginId(loginId)).thenReturn(true);

        // act & assert
        assertThatThrownBy(() -> memberService.register(loginId, password, name, birthDate, email))
            .isInstanceOf(CoreException.class);
    }

    @DisplayName("비밀번호가 8자 미만이면 예외가 발생한다")
    @Test
    void register_withShortPassword_throwsException() {
        // arrange
        String loginId = "testuser1";
        String password = "Pass1!"; // 7자
        String name = "홍길동";
        LocalDate birthDate = LocalDate.of(1990, 1, 15);
        String email = "test@example.com";

        when(memberRepository.existsByLoginId(loginId)).thenReturn(false);

        // act & assert
        assertThatThrownBy(() -> memberService.register(loginId, password, name, birthDate, email))
            .isInstanceOf(CoreException.class);
    }

    @DisplayName("비밀번호에 생년월일(yyyyMMdd)이 포함되면 예외가 발생한다")
    @Test
    void register_withBirthDateInPassword_throwsException() {
        // arrange
        String loginId = "testuser1";
        String password = "Pass19900115!"; // 생년월일 포함
        String name = "홍길동";
        LocalDate birthDate = LocalDate.of(1990, 1, 15);
        String email = "test@example.com";

        when(memberRepository.existsByLoginId(loginId)).thenReturn(false);

        // act & assert
        assertThatThrownBy(() -> memberService.register(loginId, password, name, birthDate, email))
            .isInstanceOf(CoreException.class);
    }

    @DisplayName("비밀번호 변경 시 현재 비밀번호가 일치하지 않으면 예외가 발생한다")
    @Test
    void changePassword_withWrongCurrentPassword_throwsException() {
        // arrange
        MemberModel member = new MemberModel(
            "testuser1",
            "encodedPassword",
            "홍길동",
            LocalDate.of(1990, 1, 15),
            "test@example.com"
        );

        when(passwordEncoder.matches("wrongPassword", "encodedPassword")).thenReturn(false);

        // act & assert
        assertThatThrownBy(() -> memberService.changePassword(member, "wrongPassword", "NewPassword1!"))
            .isInstanceOf(CoreException.class);
    }

    @DisplayName("비밀번호 변경 시 새 비밀번호가 현재 비밀번호와 동일하면 예외가 발생한다")
    @Test
    void changePassword_withSamePassword_throwsException() {
        // arrange
        MemberModel member = new MemberModel(
            "testuser1",
            "encodedPassword",
            "홍길동",
            LocalDate.of(1990, 1, 15),
            "test@example.com"
        );

        when(passwordEncoder.matches("Password1!", "encodedPassword")).thenReturn(true);

        // act & assert
        assertThatThrownBy(() -> memberService.changePassword(member, "Password1!", "Password1!"))
            .isInstanceOf(CoreException.class);
    }

    @DisplayName("비밀번호 변경 시 새 비밀번호가 규칙을 위반하면 예외가 발생한다")
    @Test
    void changePassword_withInvalidNewPassword_throwsException() {
        // arrange
        MemberModel member = new MemberModel(
            "testuser1",
            "encodedPassword",
            "홍길동",
            LocalDate.of(1990, 1, 15),
            "test@example.com"
        );

        when(passwordEncoder.matches("Password1!", "encodedPassword")).thenReturn(true);

        // act & assert - 7자 비밀번호
        assertThatThrownBy(() -> memberService.changePassword(member, "Password1!", "Short1!"))
            .isInstanceOf(CoreException.class);
    }

    @DisplayName("비밀번호 변경 시 새 비밀번호에 생년월일이 포함되면 예외가 발생한다")
    @Test
    void changePassword_withBirthDateInNewPassword_throwsException() {
        // arrange
        MemberModel member = new MemberModel(
            "testuser1",
            "encodedPassword",
            "홍길동",
            LocalDate.of(1990, 1, 15),
            "test@example.com"
        );

        when(passwordEncoder.matches("Password1!", "encodedPassword")).thenReturn(true);

        // act & assert - 생년월일 포함
        assertThatThrownBy(() -> memberService.changePassword(member, "Password1!", "Pass19900115!"))
            .isInstanceOf(CoreException.class);
    }

    @DisplayName("비밀번호 변경이 정상적으로 완료되면 암호화된 새 비밀번호가 저장된다")
    @Test
    void changePassword_withValidInput_updatesPassword() {
        // arrange
        MemberModel member = new MemberModel(
            "testuser1",
            "encodedOldPassword",
            "홍길동",
            LocalDate.of(1990, 1, 15),
            "test@example.com"
        );

        when(passwordEncoder.matches("OldPassword1!", "encodedOldPassword")).thenReturn(true);
        when(passwordEncoder.encode("NewPassword1!")).thenReturn("encodedNewPassword");

        // act
        memberService.changePassword(member, "OldPassword1!", "NewPassword1!");

        // assert
        assertThat(member.getPassword()).isEqualTo("encodedNewPassword");
    }
}
