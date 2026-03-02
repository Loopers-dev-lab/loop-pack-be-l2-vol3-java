package com.loopers.application.member;

import com.loopers.application.member.dto.AddMemberReqDto;
import com.loopers.application.member.dto.FindMemberResDto;
import com.loopers.application.member.dto.PutMemberPasswordReqDto;
import com.loopers.domain.member.model.Member;
import com.loopers.domain.member.model.MemberCommand;
import com.loopers.domain.member.service.MemberService;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MemberFacadeTest {

    @InjectMocks
    private MemberFacade memberFacade;

    @Mock
    private MemberService service;

    private static Member createTestMember() {
        return Member.reconstruct(1L, "testuser", "encodedPw", "홍길동", LocalDate.of(1990, 1, 1), "test@test.com");
    }

    private static AddMemberReqDto createAddMemberReqDto() {
        return new AddMemberReqDto("testuser", "Password1!", "홍길동", LocalDate.of(1990, 1, 1), "test@test.com");
    }

    private static PutMemberPasswordReqDto createPutMemberPasswordReqDto() {
        return new PutMemberPasswordReqDto("testuser", "Password1!", "Password1!", "NewPassword1!");
    }

    @DisplayName("회원 가입")
    @Nested
    class AddMember {

        @DisplayName("정상 가입 시 service.addMember가 호출된다")
        @Test
        void callsServiceAddMember_onSuccess() {
            // arrange
            AddMemberReqDto dto = createAddMemberReqDto();

            // act
            memberFacade.addMember(dto);

            // assert
            verify(service).addMember(any(MemberCommand.SignUp.class));
        }
    }

    @DisplayName("회원 조회")
    @Nested
    class FindMember {

        @DisplayName("인증에 실패하면 CoreException(UNAUTHORIZED)이 발생한다")
        @Test
        void throwsException_whenAuthFails() {
            // arrange
            when(service.findMember("testuser", "wrongpw"))
                    .thenThrow(new CoreException(ErrorType.UNAUTHORIZED, "인증에 실패했습니다."));

            // act & assert
            assertThatThrownBy(() -> memberFacade.findMember("testuser", "wrongpw"))
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.UNAUTHORIZED));
        }

        @DisplayName("정상 조회 시 회원 정보를 반환한다")
        @Test
        void returnsMemberInfo_onSuccess() {
            // arrange
            Member member = createTestMember();
            when(service.findMember("testuser", "Password1!")).thenReturn(member);

            // act
            FindMemberResDto result = memberFacade.findMember("testuser", "Password1!");

            // assert
            assertAll(
                () -> assertThat(result.loginId()).isEqualTo("testuser"),
                () -> assertThat(result.birthDate()).isEqualTo(LocalDate.of(1990, 1, 1)),
                () -> assertThat(result.email()).isEqualTo("test@test.com")
            );
        }
    }

    @DisplayName("비밀번호 변경")
    @Nested
    class PutPassword {

        @DisplayName("loginPassword와 currentPassword가 다르면 service에서 BAD_REQUEST가 발생한다")
        @Test
        void throwsException_whenLoginPasswordDiffersFromCurrentPassword() {
            // arrange
            PutMemberPasswordReqDto dto = new PutMemberPasswordReqDto(
                "testuser", "Password1!", "DifferentPass1!", "NewPassword1!"
            );
            doThrow(new CoreException(ErrorType.BAD_REQUEST, "인증 비밀번호와 현재 비밀번호가 일치하지 않습니다."))
                .when(service).changePassword(any(MemberCommand.ChangePassword.class));

            // act & assert
            assertThatThrownBy(() -> memberFacade.putPassword(dto))
                .isInstanceOf(CoreException.class)
                .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.BAD_REQUEST));
        }

        @DisplayName("정상 변경 시 service.changePassword가 호출된다")
        @Test
        void callsServiceChangePassword_onSuccess() {
            // arrange
            PutMemberPasswordReqDto dto = createPutMemberPasswordReqDto();

            // act
            memberFacade.putPassword(dto);

            // assert
            verify(service).changePassword(any(MemberCommand.ChangePassword.class));
        }
    }
}
