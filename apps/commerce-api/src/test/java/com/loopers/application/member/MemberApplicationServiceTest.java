package com.loopers.application.member;

import com.loopers.application.member.command.ChangePasswordCommand;
import com.loopers.application.member.command.RegisterCommand;
import com.loopers.domain.member.PasswordEncoder;
import com.loopers.domain.member.Member;
import com.loopers.domain.member.MemberRepository;
import com.loopers.domain.member.vo.BirthDate;
import com.loopers.domain.member.vo.Email;
import com.loopers.domain.member.vo.Name;
import com.loopers.domain.member.vo.Password;
import com.loopers.domain.member.vo.Phone;
import com.loopers.domain.member.vo.MemberId;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MemberApplicationServiceTest {

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private MemberApplicationService memberApplicationService;

    private MemberId memberId;
    private Name name;
    private Email email;
    private BirthDate birthDate;
    private Phone phone;
    private Member member;

    @BeforeEach
    void setUp() {
        memberId = new MemberId("testmember");
        name = new Name("홍길동");
        email = new Email("test@example.com");
        birthDate = new BirthDate(LocalDate.of(1999, 1, 15));
        phone = new Phone("010-1234-5678");
        member = new Member(memberId, Password.ofEncoded("$2a$10$encodedPassword"), name, email, birthDate, phone);
    }

    @Nested
    @DisplayName("회원가입")
    class RegisterTest {

        @Test
        @DisplayName("성공")
        void registerSuccess() {
            RegisterCommand command = RegisterCommand.builder()
                    .memberId("testmember")
                    .rawPassword("1Q2w3e4r!")
                    .name("홍길동")
                    .email("test@example.com")
                    .birthDate("19990115")
                    .phone("010-1234-5678")
                    .build();
            Member encodedMember = new Member(memberId, Password.ofEncoded("$2a$10$encodedPassword"), name, email, birthDate, phone);

            when(memberRepository.existsByMemberId(any(MemberId.class))).thenReturn(false);
            when(passwordEncoder.encode("1Q2w3e4r!")).thenReturn("$2a$10$encodedPassword");
            when(memberRepository.save(encodedMember)).thenReturn(encodedMember);

            Member result = memberApplicationService.register(command);

            assertThat(result.id().value()).isEqualTo("testmember");
            assertThat(result.password().value()).isEqualTo("$2a$10$encodedPassword");
            assertThat(result.phone().value()).isEqualTo("010-1234-5678");
        }

        @Test
        @DisplayName("실패 - 로그인 ID 중복")
        void registerFailDuplicateMemberId() {
            RegisterCommand command = RegisterCommand.builder()
                    .memberId("testmember")
                    .rawPassword("1Q2w3e4r!")
                    .name("홍길동")
                    .email("test@example.com")
                    .birthDate("19990115")
                    .phone("010-1234-5678")
                    .build();
            when(memberRepository.existsByMemberId(any(MemberId.class))).thenReturn(true);

            assertThatThrownBy(() -> memberApplicationService.register(command))
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.CONFLICT));
            verify(memberRepository, never()).save(any(Member.class));
        }

        @Test
        @DisplayName("실패 - 저장 시점 중복")
        void registerFailDuplicateMemberIdAtSave() {
            RegisterCommand command = RegisterCommand.builder()
                    .memberId("testmember")
                    .rawPassword("1Q2w3e4r!")
                    .name("홍길동")
                    .email("test@example.com")
                    .birthDate("19990115")
                    .phone("010-1234-5678")
                    .build();
            when(memberRepository.existsByMemberId(any(MemberId.class))).thenReturn(false);
            when(passwordEncoder.encode("1Q2w3e4r!")).thenReturn("$2a$10$encodedPassword");
            when(memberRepository.save(any(Member.class))).thenThrow(new DataIntegrityViolationException("duplicate key"));

            assertThatThrownBy(() -> memberApplicationService.register(command))
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.CONFLICT));
        }
    }

    @Nested
    @DisplayName("로그인 ID 중복 검사")
    class DuplicateCheckTest {

        @Test
        @DisplayName("사용 가능한 아이디 - false 반환")
        void checkDuplicateLoginId_available() {
            when(memberRepository.existsByMemberId(memberId)).thenReturn(false);

            boolean result = memberApplicationService.checkDuplicateLoginId("testmember");

            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("이미 사용 중인 아이디 - true 반환")
        void checkDuplicateLoginId_unavailable() {
            when(memberRepository.existsByMemberId(memberId)).thenReturn(true);

            boolean result = memberApplicationService.checkDuplicateLoginId("testmember");

            assertThat(result).isTrue();
        }
    }

    @Nested
    @DisplayName("비밀번호 변경")
    class ChangePasswordTest {

        @Test
        @DisplayName("성공")
        void changePasswordSuccess() {
            ChangePasswordCommand command = ChangePasswordCommand.builder()
                    .memberId(memberId)
                    .newRawPassword("New1234!@")
                    .build();
            Member updatedMember = new Member(memberId, Password.ofEncoded("$2a$10$newEncodedPassword"), name, email, birthDate, phone);

            when(memberRepository.findByMemberId(memberId)).thenReturn(Optional.of(member));
            when(passwordEncoder.matches("New1234!@", "$2a$10$encodedPassword")).thenReturn(false);
            when(passwordEncoder.encode("New1234!@")).thenReturn("$2a$10$newEncodedPassword");

            assertThatNoException().isThrownBy(() -> memberApplicationService.changePassword(command));
            verify(memberRepository).save(updatedMember);
        }

        @Test
        @DisplayName("실패 - 존재하지 않는 사용자")
        void changePasswordFailMemberNotFound() {
            ChangePasswordCommand command = ChangePasswordCommand.builder()
                    .memberId(memberId)
                    .newRawPassword("New1234!@")
                    .build();
            when(memberRepository.findByMemberId(memberId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> memberApplicationService.changePassword(command))
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.NOT_FOUND));
            verify(memberRepository, never()).save(any(Member.class));
        }

        @Test
        @DisplayName("실패 - 새 비밀번호가 기존과 동일")
        void changePasswordFailSamePassword() {
            ChangePasswordCommand command = ChangePasswordCommand.builder()
                    .memberId(memberId)
                    .newRawPassword("same")
                    .build();
            when(memberRepository.findByMemberId(memberId)).thenReturn(Optional.of(member));
            when(passwordEncoder.matches("same", "$2a$10$encodedPassword")).thenReturn(true);

            assertThatThrownBy(() -> memberApplicationService.changePassword(command))
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.BAD_REQUEST));
            verify(memberRepository, never()).save(any(Member.class));
        }
    }
}
