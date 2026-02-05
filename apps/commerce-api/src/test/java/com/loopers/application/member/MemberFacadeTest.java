package com.loopers.application.member;

import com.loopers.domain.member.Member;
import com.loopers.domain.member.MemberService;
import com.loopers.domain.member.SignupCommand;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * [단위 테스트 - Facade with Mock]
 *
 * 테스트 대상: MemberFacade (Application Layer)
 * 테스트 유형: 단위 테스트 (Mock 사용)
 * 테스트 더블: Mock (MemberService)
 *
 * 사용 라이브러리:
 * - JUnit 5 (org.junit.jupiter)
 * - Mockito (org.mockito)
 * - AssertJ (org.assertj.core.api)
 *
 * 어노테이션 설명:
 * - @ExtendWith(MockitoExtension.class): Mockito-JUnit 5 통합
 * - @Mock: MemberService를 Mock 객체로 생성
 * - @InjectMocks: Mock을 MemberFacade에 주입
 *
 * Mockito 메서드 설명:
 * - mock(Class): 특정 클래스의 Mock 객체 동적 생성
 * - when().thenReturn(): Stub - 메서드 호출 시 반환값 지정
 * - verify(): Mock - 메서드 호출 여부/횟수 검증
 *
 * 특징:
 * - Spring Context 불필요 → 빠른 실행
 * - Docker/DB 불필요
 * - Facade가 Service를 올바르게 호출하는지 검증
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("MemberFacade 단위 테스트")
class MemberFacadeTest {

    @Mock
    private MemberService memberService;

    @InjectMocks
    private MemberFacade memberFacade;

    @Nested
    @DisplayName("회원가입을 할 때,")
    class Signup {

        @Test
        @DisplayName("MemberService를 호출하고 MemberInfo로 변환하여 반환한다.")
        void callsServiceAndReturnsMemberInfo() {
            // arrange
            SignupCommand command = new SignupCommand(
                "testuser1",
                "Password1!",
                "홍길동",
                "test@example.com",
                "19990101"
            );

            // Stub - Member Mock 객체 설정
            Member mockMember = mock(Member.class);
            when(mockMember.getId()).thenReturn(1L);
            when(mockMember.getLoginId()).thenReturn("testuser1");
            when(mockMember.getName()).thenReturn("홍길동");
            when(mockMember.getEmail()).thenReturn("test@example.com");
            when(mockMember.getBirthDate()).thenReturn("19990101");

            when(memberService.signup(any(SignupCommand.class))).thenReturn(mockMember);

            // act
            MemberInfo info = memberFacade.signup(command);

            // assert
            assertAll(
                () -> assertThat(info.id()).isEqualTo(1L),
                () -> assertThat(info.loginId()).isEqualTo("testuser1"),
                () -> assertThat(info.name()).isEqualTo("홍길동"),
                () -> assertThat(info.email()).isEqualTo("test@example.com"),
                () -> assertThat(info.birthDate()).isEqualTo("19990101"),
                () -> verify(memberService, times(1)).signup(command)
            );
        }

        @Test
        @DisplayName("전달받은 SignupCommand를 그대로 MemberService에 전달한다.")
        void passesCommandToService() {
            // arrange
            SignupCommand command = new SignupCommand(
                "testuser1",
                "Password1!",
                "홍길동",
                "test@example.com",
                "19990101"
            );

            Member mockMember = mock(Member.class);
            when(mockMember.getId()).thenReturn(1L);
            when(mockMember.getLoginId()).thenReturn("testuser1");
            when(mockMember.getName()).thenReturn("홍길동");
            when(mockMember.getEmail()).thenReturn("test@example.com");
            when(mockMember.getBirthDate()).thenReturn("19990101");

            when(memberService.signup(command)).thenReturn(mockMember);

            // act
            memberFacade.signup(command);

            // assert - 정확히 동일한 command 객체가 전달되었는지 검증
            verify(memberService).signup(command);
        }

        @Test
        @DisplayName("MemberService에서 중복 ID 예외 발생 시 그대로 전파한다.")
        void throwsException_whenDuplicateLoginId() {
            // given
            SignupCommand command = new SignupCommand(
                "duplicateId",
                "Password1!",
                "홍길동",
                "test@example.com",
                "19990101"
            );

            when(memberService.signup(command))
                .thenThrow(new CoreException(ErrorType.CONFLICT, "이미 사용 중인 로그인 ID입니다."));

            // when & then
            assertThatThrownBy(() -> memberFacade.signup(command))
                .isInstanceOf(CoreException.class)
                .satisfies(exception -> {
                    CoreException coreException = (CoreException) exception;
                    assertThat(coreException.getErrorType()).isEqualTo(ErrorType.CONFLICT);
                });
        }

        @Test
        @DisplayName("MemberService에서 이메일 중복 예외 발생 시 그대로 전파한다.")
        void throwsException_whenDuplicateEmail() {
            // given
            SignupCommand command = new SignupCommand(
                "testuser1",
                "Password1!",
                "홍길동",
                "duplicate@example.com",
                "19990101"
            );

            when(memberService.signup(command))
                .thenThrow(new CoreException(ErrorType.CONFLICT, "이미 사용 중인 이메일입니다."));

            // when & then
            assertThatThrownBy(() -> memberFacade.signup(command))
                .isInstanceOf(CoreException.class)
                .satisfies(exception -> {
                    CoreException coreException = (CoreException) exception;
                    assertThat(coreException.getErrorType()).isEqualTo(ErrorType.CONFLICT);
                });
        }
    }

    @Nested
    @DisplayName("내 정보 조회를 할 때,")
    class GetMyInfo {

        @Test
        @DisplayName("MemberService로 인증 후 마스킹된 이름으로 MyInfo를 반환한다.")
        void authenticatesAndReturnsMaskedMyInfo() {
            // arrange
            String loginId = "testuser1";
            String rawPassword = "Password1!";

            Member mockMember = mock(Member.class);
            when(mockMember.getLoginId()).thenReturn("testuser1");
            when(mockMember.getName()).thenReturn("홍길동");
            when(mockMember.getEmail()).thenReturn("test@example.com");
            when(mockMember.getBirthDate()).thenReturn("19990101");

            when(memberService.authenticate(loginId, rawPassword)).thenReturn(mockMember);

            // act
            MyInfo info = memberFacade.getMyInfo(loginId, rawPassword);

            // assert
            assertAll(
                () -> assertThat(info.loginId()).isEqualTo("testuser1"),
                () -> assertThat(info.name()).isEqualTo("홍길*"),  // 마스킹된 이름
                () -> assertThat(info.email()).isEqualTo("test@example.com"),
                () -> assertThat(info.birthDate()).isEqualTo("19990101"),
                () -> verify(memberService, times(1)).authenticate(loginId, rawPassword)
            );
        }

        @Test
        @DisplayName("2글자 이름인 경우 마지막 글자가 마스킹된다.")
        void masksLastCharacter_when2CharacterName() {
            // arrange
            String loginId = "testuser1";
            String rawPassword = "Password1!";

            Member mockMember = mock(Member.class);
            when(mockMember.getLoginId()).thenReturn("testuser1");
            when(mockMember.getName()).thenReturn("홍길");
            when(mockMember.getEmail()).thenReturn("test@example.com");
            when(mockMember.getBirthDate()).thenReturn("19990101");

            when(memberService.authenticate(loginId, rawPassword)).thenReturn(mockMember);

            // act
            MyInfo info = memberFacade.getMyInfo(loginId, rawPassword);

            // assert
            assertThat(info.name()).isEqualTo("홍*");
        }

        @Test
        @DisplayName("존재하지 않는 로그인 ID로 조회 시 예외가 전파된다.")
        void throwsException_whenLoginIdNotFound() {
            // given
            String loginId = "nonexistent";
            String rawPassword = "Password1!";

            when(memberService.authenticate(loginId, rawPassword))
                .thenThrow(new CoreException(ErrorType.UNAUTHORIZED, "아이디 또는 비밀번호가 올바르지 않습니다."));

            // when & then
            assertThatThrownBy(() -> memberFacade.getMyInfo(loginId, rawPassword))
                .isInstanceOf(CoreException.class)
                .satisfies(exception -> {
                    CoreException coreException = (CoreException) exception;
                    assertThat(coreException.getErrorType()).isEqualTo(ErrorType.UNAUTHORIZED);
                });
        }

        @Test
        @DisplayName("비밀번호가 일치하지 않으면 예외가 전파된다.")
        void throwsException_whenPasswordMismatch() {
            // given
            String loginId = "testuser1";
            String wrongPassword = "WrongPass1!";

            when(memberService.authenticate(loginId, wrongPassword))
                .thenThrow(new CoreException(ErrorType.UNAUTHORIZED, "아이디 또는 비밀번호가 올바르지 않습니다."));

            // when & then
            assertThatThrownBy(() -> memberFacade.getMyInfo(loginId, wrongPassword))
                .isInstanceOf(CoreException.class)
                .satisfies(exception -> {
                    CoreException coreException = (CoreException) exception;
                    assertThat(coreException.getErrorType()).isEqualTo(ErrorType.UNAUTHORIZED);
                });
        }
    }

    @Nested
    @DisplayName("비밀번호 변경을 할 때,")
    class ChangePassword {

        @Test
        @DisplayName("MemberService를 호출하여 비밀번호를 변경한다.")
        void callsServiceToChangePassword() {
            // arrange
            String loginId = "testuser1";
            String headerPassword = "Password1!";
            String currentPassword = "Password1!";
            String newPassword = "NewPass123!";

            Member mockMember = mock(Member.class);
            when(memberService.authenticate(loginId, headerPassword)).thenReturn(mockMember);

            // act
            memberFacade.changePassword(loginId, headerPassword, currentPassword, newPassword);

            // assert
            verify(memberService, times(1)).authenticate(loginId, headerPassword);
            verify(memberService, times(1)).changePassword(mockMember, currentPassword, newPassword);
        }

        @Test
        @DisplayName("인증 후 비밀번호 변경이 수행된다.")
        void authenticatesBeforeChangingPassword() {
            // arrange
            String loginId = "testuser1";
            String headerPassword = "Password1!";
            String currentPassword = "Password1!";
            String newPassword = "NewPass123!";

            Member mockMember = mock(Member.class);
            when(memberService.authenticate(loginId, headerPassword)).thenReturn(mockMember);

            // act
            memberFacade.changePassword(loginId, headerPassword, currentPassword, newPassword);

            // assert - 인증이 먼저 호출됨
            var inOrder = org.mockito.Mockito.inOrder(memberService);
            inOrder.verify(memberService).authenticate(loginId, headerPassword);
            inOrder.verify(memberService).changePassword(mockMember, currentPassword, newPassword);
        }

        @Test
        @DisplayName("인증 실패 시 예외가 전파되고 비밀번호 변경은 호출되지 않는다.")
        void throwsException_whenAuthenticationFails() {
            // given
            String loginId = "testuser1";
            String wrongHeaderPassword = "WrongPass1!";
            String currentPassword = "Password1!";
            String newPassword = "NewPass123!";

            when(memberService.authenticate(loginId, wrongHeaderPassword))
                .thenThrow(new CoreException(ErrorType.UNAUTHORIZED, "아이디 또는 비밀번호가 올바르지 않습니다."));

            // when & then
            assertThatThrownBy(() -> memberFacade.changePassword(loginId, wrongHeaderPassword, currentPassword, newPassword))
                .isInstanceOf(CoreException.class)
                .satisfies(exception -> {
                    CoreException coreException = (CoreException) exception;
                    assertThat(coreException.getErrorType()).isEqualTo(ErrorType.UNAUTHORIZED);
                });

            // 인증 실패 시 changePassword는 호출되지 않음
            verify(memberService, never()).changePassword(any(), anyString(), anyString());
        }

        @Test
        @DisplayName("현재 비밀번호가 일치하지 않으면 예외가 전파된다.")
        void throwsException_whenCurrentPasswordMismatch() {
            // given
            String loginId = "testuser1";
            String headerPassword = "Password1!";
            String wrongCurrentPassword = "WrongCurrent1!";
            String newPassword = "NewPass123!";

            Member mockMember = mock(Member.class);
            when(memberService.authenticate(loginId, headerPassword)).thenReturn(mockMember);
            doThrow(new CoreException(ErrorType.UNAUTHORIZED, "현재 비밀번호가 일치하지 않습니다."))
                .when(memberService).changePassword(mockMember, wrongCurrentPassword, newPassword);

            // when & then
            assertThatThrownBy(() -> memberFacade.changePassword(loginId, headerPassword, wrongCurrentPassword, newPassword))
                .isInstanceOf(CoreException.class)
                .satisfies(exception -> {
                    CoreException coreException = (CoreException) exception;
                    assertThat(coreException.getErrorType()).isEqualTo(ErrorType.UNAUTHORIZED);
                });
        }

        @Test
        @DisplayName("새 비밀번호가 유효하지 않으면 예외가 전파된다.")
        void throwsException_whenNewPasswordInvalid() {
            // given
            String loginId = "testuser1";
            String headerPassword = "Password1!";
            String currentPassword = "Password1!";
            String invalidNewPassword = "short";

            Member mockMember = mock(Member.class);
            when(memberService.authenticate(loginId, headerPassword)).thenReturn(mockMember);
            doThrow(new CoreException(ErrorType.BAD_REQUEST, "비밀번호는 8자 이상 16자 이하여야 합니다."))
                .when(memberService).changePassword(mockMember, currentPassword, invalidNewPassword);

            // when & then
            assertThatThrownBy(() -> memberFacade.changePassword(loginId, headerPassword, currentPassword, invalidNewPassword))
                .isInstanceOf(CoreException.class)
                .satisfies(exception -> {
                    CoreException coreException = (CoreException) exception;
                    assertThat(coreException.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
                });
        }
    }
}
